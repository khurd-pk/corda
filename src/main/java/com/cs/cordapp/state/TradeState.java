package com.cs.cordapp.state;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

import org.bson.BsonBinary;
import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

import com.google.common.collect.ImmutableList;

import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;

public class TradeState implements ContractState {	
	
	private String ticker;
	private String side;
	private long amount;
	private double price;
    
	private Party initiatingParty;
	private Party counterParty;
	private String tradeStatus;

	private String tradeId;
	
    private static Codec<BsonDocument> DOCUMENT_CODEC = new BsonDocumentCodec();


	public TradeState(String ticker, String side, long amount, double price, Party initiatingParty, Party counterParty, 
			String tradeStatus, String tradeId) {
		super();
		this.ticker = ticker;
		this.side = side;
		this.amount = amount;
		this.price = price;		
		this.initiatingParty = initiatingParty;
		this.counterParty = counterParty;
		this.tradeStatus = tradeStatus;
		if(tradeId == null)
			this.tradeId = UUID.randomUUID().toString();
		else
			this.tradeId = tradeId;
	}

	
	public static void main(String args[]){        
        TradeState tradeState = new TradeState("BUY","RIC1",10000l,99.9d, null, null,"Pending", null);
    }

    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(initiatingParty,counterParty);
    }

    public byte[] getTradeStateAsBson() throws NoSuchAlgorithmException {
        BsonDocument bdoc = new BsonDocument();
        bdoc.put("tradeId", new BsonString(tradeId));
        bdoc.put("ticker", new BsonString(ticker));
        bdoc.put("side", new BsonString(side));
        bdoc.put("amount", new BsonInt64(amount));
        bdoc.put("price", new BsonDouble(price));
        byte[] payload = toInputStream(bdoc);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
        StringBuffer stringBuffer = new StringBuffer();
        for (byte bytes : digest) {
            stringBuffer.append(String.format("%02x", bytes & 0xff));
        }

        BsonDocument finaldoc = new BsonDocument();
        finaldoc.put("payload", new BsonBinary(payload));
        finaldoc.put("payloadhash", new BsonString(stringBuffer.toString()));

        return toInputStream(finaldoc);
    }

    private static byte[] toInputStream(final BsonDocument document) {
        BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(outputBuffer);
        DOCUMENT_CODEC.encode(writer, document, EncoderContext.builder().isEncodingCollectibleDocument(true).build());
        // return new ByteArrayInputStream(outputBuffer.toByteArray());
        return outputBuffer.toByteArray();
    }


	public String getTicker() {
		return ticker;
	}


	public void setTicker(String ticker) {
		this.ticker = ticker;
	}


	public String getSide() {
		return side;
	}


	public void setSide(String side) {
		this.side = side;
	}


	public long getAmount() {
		return amount;
	}


	public void setAmount(long amount) {
		this.amount = amount;
	}


	public double getPrice() {
		return price;
	}


	public void setPrice(double price) {
		this.price = price;
	}


	public Party getInitiatingParty() {
		return initiatingParty;
	}


	public void setInitiatingParty(Party initiatingParty) {
		this.initiatingParty = initiatingParty;
	}


	public Party getCounterParty() {
		return counterParty;
	}


	public void setCounterParty(Party counterparty) {
		this.counterParty = counterparty;
	}


	public String getTradeStatus() {
		return tradeStatus;
	}


	public void setTradeStatus(String tradeStatus) {
		this.tradeStatus = tradeStatus;
	}


	public String getTradeId() {
		return tradeId;
	}


	public void setTradeId(String tradeId) {
		this.tradeId = tradeId;
	}


	@Override
	public String toString() {
		return "TradeState [ticker=" + ticker + ", side=" + side + ", amount=" + amount + ", price=" + price
				+ ", initiatingParty=" + initiatingParty + ", counterparty=" + counterParty + ", tradeStatus="
				+ tradeStatus + ", tradeId=" + tradeId + "]";
	}

    
    
}
