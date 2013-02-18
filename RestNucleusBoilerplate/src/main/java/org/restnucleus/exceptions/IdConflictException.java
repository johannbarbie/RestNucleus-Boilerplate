package org.restnucleus.exceptions;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


/**
 * @author johba
 */
public class IdConflictException extends WebApplicationException {

	private static final long serialVersionUID = -7833704596278144283L;

	public IdConflictException(String message) {
        super(Response.status(Response.Status.CONFLICT)
            .entity(message).type(MediaType.TEXT_PLAIN).build());
    }

}
