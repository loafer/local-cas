package com.github.loafer.cas.ticket.registry;

import org.jasig.cas.ticket.ServiceTicket;
import org.jasig.cas.ticket.Ticket;
import org.jasig.cas.ticket.TicketGrantingTicket;
import org.jasig.cas.ticket.registry.AbstractDistributedTicketRegistry;
import org.springframework.util.SerializationUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;
import redis.clients.util.SafeEncoder;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Collection;

/**
 * @author zhaojh.
 */
public class RedisTicketRegistry extends AbstractDistributedTicketRegistry {
    @NotNull
    private final Jedis client;

    @Min(0)
    private final int tgtTimeout;

    @Min(0)
    private final int stTimeout;

    public RedisTicketRegistry(final String hostname, final int ticketGrantingTicketTimeOut, final int serviceTicketTimeOut) {
        this(hostname, 6379, ticketGrantingTicketTimeOut, serviceTicketTimeOut);
    }

    public RedisTicketRegistry(final String hostname, final int port, final int ticketGrantingTicketTimeOut, final int serviceTicketTimeOut) {
        this.client = new Jedis(hostname, port);
        this.tgtTimeout = ticketGrantingTicketTimeOut;
        this.stTimeout = serviceTicketTimeOut;
    }

    @Override
    protected void updateTicket(Ticket ticket) {
        logger.debug("Updating ticket {}", ticket);
        String status = this.client.setex(SafeEncoder.encode(ticket.getId()), getTimeout(ticket), SerializationUtils.serialize(ticket));
        if(!Protocol.Keyword.OK.name().equalsIgnoreCase(status)){
            logger.error("Failed updating {}", ticket);
        }
    }

    @Override
    public void addTicket(Ticket ticket) {
        logger.debug("Adding ticket {}", ticket);
        String status = this.client.setex(SafeEncoder.encode(ticket.getId()), getTimeout(ticket), SerializationUtils.serialize(ticket));
        if(!Protocol.Keyword.OK.name().equalsIgnoreCase(status)){
            logger.error("Failed adding {}", ticket);
        }
    }

    @Override
    public Ticket getTicket(String ticketId) {
        try{
            final Ticket t = (Ticket) SerializationUtils.deserialize(this.client.get(SafeEncoder.encode(ticketId)));
            if (t != null) {
                return getProxiedTicketInstance(t);
            }
        }catch (Exception e){
            logger.error("Failed fetching {} ", ticketId, e);
        }
        return null;
    }

    @Override
    public boolean deleteTicket(String ticketId) {
        logger.debug("Deleting ticket {}", ticketId);
        try {
            return this.client.del(SafeEncoder.encode(ticketId)) == 1;
        } catch (final Exception e) {
            logger.error("Failed deleting {}", ticketId, e);
        }
        return false;
    }

    @Override
    public Collection<Ticket> getTickets() {
        throw new UnsupportedOperationException("GetTickets not supported.");
    }

    @Override
    protected boolean needsCallback() {
        return false;
    }

    private int getTimeout(final Ticket t) {
        if (t instanceof TicketGrantingTicket) {
            return this.tgtTimeout;
        } else if (t instanceof ServiceTicket) {
            return this.stTimeout;
        }
        throw new IllegalArgumentException("Invalid ticket type");
    }
}
