package com.cs.cordapp.flow;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.List;

import com.cs.cordapp.contract.TradeContract;
import com.cs.cordapp.state.TradeState;
import com.cs.cordapp.contract.TradeContract.Create;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.CollectSignaturesFlow;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;


/**
 * This flow allows two parties (the [Creator] and the [CounterParty]) to come to an agreement about the Trade.
 *
 * In our simple trading example, the [CounterParty] always accepts a valid Trade.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */


@InitiatingFlow
@StartableByRPC
public class TradeFlow extends FlowLogic<SignedTransaction> {

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

    
    
    private final TradeState tradeState;
    

    public TradeFlow(TradeState tradeState) {
        this.tradeState = tradeState;        
    }


    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    public SignedTransaction call() throws FlowException {
    	// Obtain a reference to the notary we want to use.
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        
        // We get a reference to our own identity.
        Party initiatingParty = getOurIdentity();

        // Stage 1.
        progressTracker.setCurrentStep(GENERATING_TRANSACTION);       
        TradeContract.Create command = new TradeContract.Create();
        List<PublicKey> requiredSigns = ImmutableList.of(initiatingParty.getOwningKey(),tradeState.getCounterParty().getOwningKey());
        
        TransactionBuilder transactionBuilder = new TransactionBuilder(notary)
        		.addOutputState(tradeState, TradeContract.ID)
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
        FlowSession otherPartyFlow = initiateFlow(tradeState.getCounterParty());
        SignedTransaction fullySignedTx = subFlow(
                new CollectSignaturesFlow(partlySignedTx, ImmutableSet.of(otherPartyFlow),GATHERING_SIGS.childProgressTracker() ));
       
        // Stage 5.
        progressTracker.setCurrentStep(FINALISING_TRANSACTION);
        // Notarise and record the transaction in both parties' vaults.
       return  subFlow(new FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()));
    }
}
