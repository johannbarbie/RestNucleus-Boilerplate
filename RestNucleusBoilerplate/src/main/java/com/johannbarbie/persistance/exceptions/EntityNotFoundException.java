package com.johannbarbie.persistance.exceptions;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author johba
 */
public class EntityNotFoundException extends WebApplicationException{

	private static final long serialVersionUID = -8076128214795115306L;

	public EntityNotFoundException(String message) {
        super(Response.status(Response.Status.NOT_FOUND)
            .entity(message).type(MediaType.TEXT_PLAIN).build());
    }
}
