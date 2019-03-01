package com.cs.cordapp.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.cs.cordapp.flow.CounterTradeFlow;
import com.cs.cordapp.flow.TradeFlow;
import com.cs.cordapp.state.TradeState;
import com.google.common.collect.ImmutableMap;

import jersey.repackaged.com.google.common.collect.ImmutableList;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.NodeInfo;
import net.corda.core.transactions.SignedTransaction;

//This API is accessible from /api/trading. All paths specified below are relative to it.
@Path("trading")
public class TradeApi {
	
	private List<String> SERVICE_NAMES = ImmutableList.<String>of("Notary", "Controller");
	private final CordaRPCOps rpcOps;
	private CordaX500Name myLegalName;
	
	public TradeApi(CordaRPCOps services) {
	        this.rpcOps = services;
	        this.myLegalName = rpcOps.nodeInfo().getLegalIdentities().get(0).getName();
	       }
	/**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    public CordaX500Name whoami() {     
     return myLegalName;
    }
    
    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<CordaX500Name>> getPeers() {
    List<NodeInfo> nodes = rpcOps.networkMapSnapshot();
    //System.out.println("Peers :" + nodes);
     return ImmutableMap.<String, List<CordaX500Name>>of("peers",  
    		 nodes.stream().map(x->x.getLegalIdentities().get(0).getName())
    		 .filter(x-> !x.getOrganisation().equals(myLegalName.getOrganisation()))
    		.filter(x -> !SERVICE_NAMES.contains(x.getOrganisation()))
    		.collect(Collectors.toList()));
    }

    /**
     * Displays all Trade states that exist in the node's vault.
     */
    @GET
    @Path("trades")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<TradeState>> getTrades() {    
    	return rpcOps.vaultQuery(TradeState.class).getStates();
    }
    
	/**
	 * Initiates Create Trade Flow.
	 */
	@PUT
	@Path("create-trade")		
	public Response createTrade(@QueryParam("counterParty") CordaX500Name counterParty,
									 @QueryParam("ticker") String ticker, 
									 @QueryParam("side") String side, 
									 @QueryParam("amount") long amount,
									 @QueryParam("price") double price, 
									 @QueryParam("tradeStatus") String tradeStatus) {

		System.out.println("Create trade  command");
		if (this.rpcOps.wellKnownPartyFromX500Name(counterParty) == null) {
			return Response.status(Status.BAD_REQUEST).entity("Counter Party named " + counterParty + " cannot be found.\n").build();
		} else {
			try {			
				TradeState tradestate = new TradeState(ticker, side, amount, price, this.rpcOps.wellKnownPartyFromX500Name(myLegalName), 
						this.rpcOps.wellKnownPartyFromX500Name(counterParty), tradeStatus, null);
				
				SignedTransaction signedTx = rpcOps.startFlowDynamic(TradeFlow.class, tradestate)
																					.getReturnValue().get();
				System.out.println("signedTx.getId() =  :" + signedTx.getId());
				return Response.status(Status.CREATED).entity("Transaction id " + signedTx.getId() + " committed to ledger.\n").build();

			} catch (Exception ex) {

				return Response.status(Status.BAD_REQUEST).entity(ex.getMessage()).build();
			}
		}

	}
	/**
	 * Initiates Create Trade Flow.
	 */
	@PUT
	@Path("counter-trade")		
	public Response counterTrade(@QueryParam("tradeId") String tradeId,
									@QueryParam("counterParty") CordaX500Name counterParty,
									 @QueryParam("ticker") String ticker, 
									 @QueryParam("side") String side, 
									 @QueryParam("amount") long amount,
									 @QueryParam("price") double price, 
									 @QueryParam("tradeStatus") String tradeStatus) {

		System.out.println("Create trade  command");
		if (this.rpcOps.wellKnownPartyFromX500Name(counterParty) == null) {
			return Response.status(Status.BAD_REQUEST).entity("Counter Party named " + counterParty + " cannot be found.\n").build();
		} else {
			try {			
				TradeState tradestate = new TradeState(ticker, side, amount, price, this.rpcOps.wellKnownPartyFromX500Name(myLegalName), 
						this.rpcOps.wellKnownPartyFromX500Name(counterParty), tradeStatus, null);
				
				SignedTransaction signedTx = rpcOps.startFlowDynamic(CounterTradeFlow.class, tradestate)
																					.getReturnValue().get();
				System.out.println("signedTx.getId() =  :" + signedTx.getId());
				return Response.status(Status.CREATED).entity("Transaction id " + signedTx.getId() + " committed to ledger.\n").build();

			} catch (Exception ex) {

				return Response.status(Status.BAD_REQUEST).entity(ex.getMessage()).build();
			}
		}

	}

	
	
    /**
     * Get full Trade details.
     */
    @GET
    @Path("getTrade")
    @Produces(MediaType.APPLICATION_JSON)
    public Response gettrades(@QueryParam("tradeId") String tradeId){
        if (tradeId == null) {
            return Response.status(Status.BAD_REQUEST).entity("Query parameter 'linearID' missing or has wrong format.\n").build();
        }
//        QueryCriteria.LinearStateQueryCriteria("tradeId")
//        val idParts = linearID.split('_')
//        val uuid = idParts[idParts.size - 1]
//        		
//        val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(UniqueIdentifier.fromString(uuid)),status = Vault.StateStatus.ALL)
//        return try {
//            Response.ok(rpcOps.vaultQueryBy<TradeState>(criteria=criteria).states).build()
//        } catch (ex: Throwable) {
//            logger.error(ex.message, ex)
//            Response.status(BAD_REQUEST).entity(ex.message!!).build()
//        }
    return null; 
    }
}
