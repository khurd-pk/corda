package com.cs.cordapp.flow;

import java.security.PublicKey;
import java.util.List;
import java.util.stream.Collectors;

import com.cs.cordapp.contract.TradeContract;
import com.cs.cordapp.state.TradeState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import net.corda.core.flows.CollectSignaturesFlow;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.VaultService;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import net.corda.finance.contracts.asset.Cash;

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the Trade encapsulated
 * within an [TradeState].
 *
 * In our simple trading, the [Acceptor] always accepts a valid Trade.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */

@InitiatingFlow
@StartableByRPC
public class CounterTradeFlow  extends FlowLogic<SignedTransaction> {

	/**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
     * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
     */
	
	private Step GENERATING_TRANSACTION = new Step("Generating transaction based on new Trade.");
	private Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
	private Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
	private Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
		@Override
		public ProgressTracker childProgressTracker() {
			return CollectSignaturesFlow.tracker();
		}
	};	
	private Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
		@Override
		public ProgressTracker childProgressTracker() {
			return FinalityFlow.tracker();
		}
	};

	private final ProgressTracker progressTracker = new ProgressTracker(GENERATING_TRANSACTION,
    		VERIFYING_TRANSACTION,
    		SIGNING_TRANSACTION,
    		GATHERING_SIGS,
    		FINALISING_TRANSACTION);

    
    
    private final TradeState counterTradeState;
    

    public CounterTradeFlow(TradeState tradeState) {
        this.counterTradeState = tradeState;        
    }
	@Override
	public SignedTransaction call() throws FlowException {
		// Obtain a reference to the notary we want to use.
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        
        // We get a reference to our own identity.
        Party initiatingParty = getOurIdentity();

        // Stage 1.
        progressTracker.setCurrentStep(GENERATING_TRANSACTION);       
        VaultService vaultService = getServiceHub().getVaultService();
        
       TradeState  inputTrade=	vaultService.queryBy(TradeState.class).getStates().stream()
        																.filter(x->x.getState().getData().getTradeStatus().equalsIgnoreCase("PENDING"))
        																.filter(x->x.getState().getData().getTradeId().equals(this.counterTradeState.getTradeId()))
        																.collect(Collectors.toList())
        																.get(0).getState().getData();
       							
       counterTradeState.setTradeId(inputTrade.getTradeId());
       
        TradeContract.CounterTrade command = new TradeContract.CounterTrade();
        List<PublicKey> requiredSigns = ImmutableList.of(initiatingParty.getOwningKey(),counterTradeState.getCounterParty().getOwningKey());
        
        TransactionBuilder transactionBuilder = new TransactionBuilder(notary)
        		.addOutputState(counterTradeState, TradeContract.ID)
                .addCommand(command,requiredSigns);
        // Stage 2.
        progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
        transactionBuilder.verify(getServiceHub());

        // Stage 3.
        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
        SignedTransaction partlySignedTx = getServiceHub().signInitialTransaction(transactionBuilder);
        
        // Stage 4.
        progressTracker.setCurrentStep(GATHERING_SIGS);
        // Send the state to the counterparty, and receive it back with their signature.
        FlowSession otherPartyFlow = initiateFlow(counterTradeState.getCounterParty());
        SignedTransaction fullySignedTx = subFlow(
                new CollectSignaturesFlow(partlySignedTx, ImmutableSet.of(otherPartyFlow),GATHERING_SIGS.childProgressTracker() ));
       
        // Stage 5.
        progressTracker.setCurrentStep(FINALISING_TRANSACTION);
        // Notarise and record the transaction in both parties' vaults.
       return  subFlow(new FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()));
       
	}

}
