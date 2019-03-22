/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.db.sessions;

import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoClient;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.session.ServerSession;
import java.util.UUID;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.UuidRepresentation;
import static org.bson.assertions.Assertions.notNull;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.UuidCodec;
import org.restheart.db.MongoDBClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ClientSessionFactory {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ClientSessionFactory.class);

    private static MongoClient MCLIENT = MongoDBClientSingleton
            .getInstance()
            .getClient();

    public static ServerSession createServerSession(UUID sid) {
        return new ServerSessionImpl(createServerSessionIdentifier(sid));
    }

    private static BsonBinary createServerSessionIdentifier(UUID sid) {
        UuidCodec uuidCodec = new UuidCodec(UuidRepresentation.STANDARD);
        BsonDocument holder = new BsonDocument();
        BsonDocumentWriter bsonDocumentWriter = new BsonDocumentWriter(holder);
        bsonDocumentWriter.writeStartDocument();
        bsonDocumentWriter.writeName("id");
        uuidCodec.encode(bsonDocumentWriter,
                sid,
                EncoderContext.builder().build());
        bsonDocumentWriter.writeEndDocument();
        return holder.getBinary("id");
    }

    public static ClientSessionImpl getClientSession(UUID sid) {
        var options = Sid.getSessionOptions(sid);

        ClientSessionOptions cso = ClientSessionOptions
                .builder()
                .causallyConsistent(options.isCausallyConsistent())
                .build();

        ClientSessionImpl cs = createClientSession(
                sid,
                cso,
                MCLIENT.getReadConcern(),
                MCLIENT.getWriteConcern(),
                MCLIENT.getReadPreference(),
                null);

        if (options.isTransacted()) {
            var txnServerStatus = SessionsUtils.getTxnServerStatus(cs);
            
            cs.setTxnServerStatus(txnServerStatus);

//            if (!cs.hasActiveTransaction()) {
//                cs.startTransaction();
//            }
            if (cs.getServerSession().getTransactionNumber() 
                    < txnServerStatus.getTxnId()) {
                ((ServerSessionImpl) cs.getServerSession())
                        .advanceTransactionNumber(txnServerStatus.getTxnId());
            }

            switch (txnServerStatus.getState()) {
                case IN:
                    cs.setMessageSentInCurrentTransaction(true);
                    break;
                case ABORTED:
                case COMMITTED:
//                    cs.getServerSession().advanceTransactionNumber();
//                    cs.setMessageSentInCurrentTransaction(false);
//                    txnServerStatus = new Txn(cs.getServerSession().getTransactionNumber(),
//                            Txn.TransactionState.IN);
                    break;
                case NONE:
//                    cs.setMessageSentInCurrentTransaction(false);
//                    if (!cs.hasActiveTransaction()) {
//                        cs.startTransaction();
//                    }
                    break;
                default:
                    throw new IllegalStateException("Session "
                            + sid
                            + " has unknown txn status "
                            + txnServerStatus);
            }
        } 

        return cs;
    }

    static ClientSessionImpl createClientSession(
            UUID sid,
            final ClientSessionOptions options,
            final ReadConcern readConcern,
            final WriteConcern writeConcern,
            final ReadPreference readPreference,
            final Txn txnServerStatus) {
        notNull("readConcern", readConcern);
        notNull("writeConcern", writeConcern);
        notNull("readPreference", readPreference);

        // TODO allow request to specify session and txn options
        ClientSessionOptions mergedOptions = ClientSessionOptions
                .builder(options)
                .causallyConsistent(true)
                .defaultTransactionOptions(
                        TransactionOptions.merge(
                                options.getDefaultTransactionOptions(),
                                TransactionOptions.builder()
                                        .readConcern(readConcern)
                                        .writeConcern(writeConcern)
                                        .readPreference(readPreference)
                                        .build()))
                .build();

        ClientSessionImpl cs = new ClientSessionImpl(
                new SimpleServerSessionPool(SessionsUtils.getCluster(), sid),
                MCLIENT,
                mergedOptions,
                SessionsUtils.getMongoClientDelegate(),
                txnServerStatus);

        return cs;

    }
}

final class ServerSessionImpl implements ServerSession {
    interface Clock {
        long millis();
    }

    private Clock clock = new Clock() {
        @Override
        public long millis() {
            return System.currentTimeMillis();
        }
    };

    private final BsonDocument identifier;
    private long transactionNumber = 0;
    private volatile long lastUsedAtMillis = clock.millis();
    private volatile boolean closed;

    ServerSessionImpl(final BsonBinary identifier) {
        this.identifier = new BsonDocument("id", identifier);
    }

    void close() {
        closed = true;
    }

    long getLastUsedAtMillis() {
        return lastUsedAtMillis;
    }

    @Override
    public long getTransactionNumber() {
        return transactionNumber;
    }

    @Override
    public BsonDocument getIdentifier() {
        lastUsedAtMillis = clock.millis();
        return identifier;
    }

    @Override
    public long advanceTransactionNumber() {
        return transactionNumber++;
    }

    public void advanceTransactionNumber(long number) {
        if (number <= this.transactionNumber) {
            throw new IllegalArgumentException("current transactionNumber is "
                    + this.transactionNumber
                    + " cannot set it to "
                    + number);
        }

        this.transactionNumber = number;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}