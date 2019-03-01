package com.cs.cordapp.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.bson.BsonBinary;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

import com.cs.cordapp.state.TradeState;
import com.cs.cordapp.db.MongoHelper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCClientConfiguration;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import net.corda.core.utilities.NetworkHostAndPort;
import rx.Observable;

public class TradeClientRPC {

    private static Codec<BsonDocument> DOCUMENT_CODEC = new BsonDocumentCodec();
    
    public static void main(String[] args) throws ActiveMQException, InterruptedException, ExecutionException {
    	
    	 if (args.length != 1) {
             throw new IllegalArgumentException("Usage: TemplateClient <node address>");
         }

    	final NetworkHostAndPort nodeAddress = NetworkHostAndPort.parse(args[0]);//NetworkHostAndPort.parse("localhost:10004");
        final CordaRPCClient client = new CordaRPCClient(nodeAddress, CordaRPCClientConfiguration.DEFAULT);

        // Can be amended in the com.example.Main file.
        final CordaRPCOps proxy = client.start("user1", "test").getProxy();

        // Grab all existing and future IOU states in the vault.

        final DataFeed<Vault.Page<TradeState>, Vault.Update<TradeState>> dataFeed = proxy.vaultTrack(TradeState.class);
        final Vault.Page<TradeState> snapshot = dataFeed.getSnapshot();
        final Observable<Vault.Update<TradeState>> updates = dataFeed.getUpdates();

        // Log the existing TradeStates and listen for new ones.
        snapshot.getStates().forEach(TradeClientRPC::logState);
        updates.toBlocking().subscribe(update -> update.getProduced().forEach(TradeClientRPC::logState));
    }

    // Log the trade state and also convert ito binary stream so to insert into Mongo db
    
    private static void logState(StateAndRef<TradeState> state) {
        System.out.println("{Data: }"+ state.getState().getData());
        System.out.println("{Contract: }"+ state.getState().getContract());
        System.out.println("{Hash: }"+ state.getRef().getTxhash().toString());
        try{
            byte[] tradeStateAsBson = state.getState().getData().getTradeStateAsBson();
            BsonDocument bsonDocument = fromInputStream(new ByteArrayInputStream(tradeStateAsBson));
            byte [] payload = ((BsonBinary)bsonDocument.get("payload")).getData();
            BsonDocument bsonDocumentReal = fromInputStream(new ByteArrayInputStream(payload));
            System.out.println("{payload: }"+ bsonDocumentReal.toString());
            BsonValue trader = bsonDocumentReal.get("trader");
            System.out.println("{trader: }"+ trader.toString());
            BsonValue payloadhash = bsonDocument.get("payloadhash");
            System.out.println("{payloadhash: }"+ payloadhash.asString().getValue());

            BsonDocument mongoDbDoc = new BsonDocument();
            mongoDbDoc.put("data", new BsonString(bsonDocumentReal.toString()));
            mongoDbDoc.put("hash", new BsonString(payloadhash.asString().getValue()));
            mongoDbDoc.put("contractId", new BsonString(UUID.randomUUID().toString()));

            MongoHelper mongoHelper = new MongoHelper();
            MongoDatabase cordaDb = mongoHelper.getDatabase();
            MongoCollection<BsonDocument> ledger = cordaDb.getCollection("ledger", BsonDocument.class);
            ledger.insertOne(mongoDbDoc);
            System.out.println("Bson Record Inserted in MongoDb");
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public static byte[] toInputStream(final BsonDocument document) {
        BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(outputBuffer);
        DOCUMENT_CODEC.encode(writer, document, EncoderContext.builder().isEncodingCollectibleDocument(true).build());
       // return new ByteArrayInputStream(outputBuffer.toByteArray());
        return outputBuffer.toByteArray();
    }

   public static BsonDocument fromInputStream(final InputStream input) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = input.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
        }
        outputStream.close();
        BsonBinaryReader bsonReader = new BsonBinaryReader(ByteBuffer.wrap(outputStream.toByteArray()));
        return DOCUMENT_CODEC.decode(bsonReader, DecoderContext.builder().build());
    }

   
}