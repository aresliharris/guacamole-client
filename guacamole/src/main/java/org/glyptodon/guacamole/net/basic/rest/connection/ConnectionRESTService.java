package org.glyptodon.guacamole.net.basic.rest.connection;

/*
 *  Guacamole - Clientless Remote Desktop
 *  Copyright (C) 2010  Michael Jumper
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import org.glyptodon.guacamole.GuacamoleClientException;
import org.glyptodon.guacamole.GuacamoleException;
import org.glyptodon.guacamole.GuacamoleSecurityException;
import org.glyptodon.guacamole.net.auth.Connection;
import org.glyptodon.guacamole.net.auth.ConnectionGroup;
import org.glyptodon.guacamole.net.auth.Directory;
import org.glyptodon.guacamole.net.auth.UserContext;
import org.glyptodon.guacamole.net.basic.rest.HTTPException;
import org.glyptodon.guacamole.net.basic.rest.auth.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A REST Service for handling connection CRUD operations.
 * 
 * @author James Muehlner
 */
@Path("/api/connection")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConnectionRESTService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(ConnectionRESTService.class);
    
    /**
     * A service for authenticating users from auth tokens.
     */
    @Inject
    private AuthenticationService authenticationService;
    
    /**
     * A service for managing the REST endpoint APIConnection objects. 
     */
    @Inject
    private ConnectionService connectionService;
    
    /**
     * Gets a list of connections with the given ConnectionGroup parentID.
     * If no parentID is provided, returns the connections from the root group.
     * 
     * @param authToken The authentication token that is used to authenticate
     *                  the user performing the operation.
     * @param parentID The ID of the ConnectionGroup the connections
     *                 belong to. If null, the root connection group will be used.
     * @return The connection list.
     */
    @GET
    public List<APIConnection> getConnections(@QueryParam("token") String authToken, @QueryParam("parentID") String parentID) {
        UserContext userContext = authenticationService.getUserContextFromAuthToken(authToken);
        
        try {
            // If the parent connection group is passed in, try to find it.
            ConnectionGroup parentConnectionGroup;
            if(parentID == null)
                parentConnectionGroup = userContext.getRootConnectionGroup();
            else {
                ConnectionGroup rootGroup = userContext.getRootConnectionGroup();
                Directory<String, ConnectionGroup> connectionGroupDirectory = rootGroup.getConnectionGroupDirectory();
                parentConnectionGroup = connectionGroupDirectory.get(parentID);
            }
            
            if(parentConnectionGroup == null)
                throw new HTTPException(Status.NOT_FOUND, "No ConnectionGroup found with the provided parentID.");
            
            Directory<String, Connection> connectionDirectory = 
                    parentConnectionGroup.getConnectionDirectory();
            
            // Return the converted connection directory
            return connectionService.convertConnectionList(connectionDirectory);
        } catch(GuacamoleSecurityException e) {
            throw new HTTPException(Status.FORBIDDEN, e.getMessage() != null ? e.getMessage() : "Permission denied.");
        } catch(GuacamoleClientException e) {
            throw new HTTPException(Status.BAD_REQUEST, e.getMessage() != null ? e.getMessage() : "Invalid Request.");
        } catch(GuacamoleException e) {
            logger.error("Unexpected GuacamoleException caught while listing connections.", e);
            throw new HTTPException(Status.INTERNAL_SERVER_ERROR, e.getMessage() != null ? e.getMessage() : "Unexpected server error.");
        }
    }
    
    /**
     * Gets an individual connection.
     * 
     * @param authToken The authentication token that is used to authenticate
     *                  the user performing the operation.
     * @param connectionID The ID of the Connection..
     * @return The connection.
     */
    @GET
    @Path("/{connectionID}")
    public APIConnection getConnection(@QueryParam("token") String authToken, 
            @PathParam("connectionID") String connectionID) {
        UserContext userContext = authenticationService.getUserContextFromAuthToken(authToken);
        
        try {
            // Get the connection directory
            ConnectionGroup rootGroup = userContext.getRootConnectionGroup();
            Directory<String, Connection> connectionDirectory =
                    rootGroup.getConnectionDirectory();
            
            // Get the connection
            Connection connection = connectionDirectory.get(connectionID);
            
            if(connection == null)
                throw new HTTPException(Status.NOT_FOUND, "No Connection found with the provided ID.");
            
            return new APIConnection(connection);
        } catch(GuacamoleSecurityException e) {
            throw new HTTPException(Status.FORBIDDEN, e.getMessage() != null ? e.getMessage() : "Permission denied.");
        } catch(GuacamoleClientException e) {
            throw new HTTPException(Status.BAD_REQUEST, e.getMessage() != null ? e.getMessage() : "Invalid Request.");
        } catch(GuacamoleException e) {
            logger.error("Unexpected GuacamoleException caught while getting connection.", e);
            throw new HTTPException(Status.INTERNAL_SERVER_ERROR, e.getMessage() != null ? e.getMessage() : "Unexpected server error.");
        }
    }
    
    /**
     * Deletes an individual connection.
     * 
     * @param authToken The authentication token that is used to authenticate
     *                  the user performing the operation.
     * @param connectionID The ID of the Connection to delete.
     */
    @DELETE
    @Path("/{connectionID}")
    public void deleteConnection(@QueryParam("token") String authToken, @PathParam("connectionID") String connectionID) {
        UserContext userContext = authenticationService.getUserContextFromAuthToken(authToken);
        
        try {
            // Get the connection directory
            ConnectionGroup rootGroup = userContext.getRootConnectionGroup();
            Directory<String, Connection> connectionDirectory =
                    rootGroup.getConnectionDirectory();
            
            // Make sure the connection is there before trying to delete
            if(connectionDirectory.get(connectionID) == null)
                throw new HTTPException(Status.NOT_FOUND, "No Connection found with the provided ID.");
            
            // Delete the connection
            connectionDirectory.remove(connectionID);
        } catch(GuacamoleSecurityException e) {
            throw new HTTPException(Status.FORBIDDEN, e.getMessage() != null ? e.getMessage() : "Permission denied.");
        } catch(GuacamoleClientException e) {
            throw new HTTPException(Status.BAD_REQUEST, e.getMessage() != null ? e.getMessage() : "Invalid Request.");
        } catch(GuacamoleException e) {
            logger.error("Unexpected GuacamoleException caught while deleting connection.", e);
            throw new HTTPException(Status.INTERNAL_SERVER_ERROR, e.getMessage() != null ? e.getMessage() : "Unexpected server error.");
        }
    }
    
    /**
     * Creates a new connection and returns the identifier of the new connection.
     * If a parentID is provided, the connection will be created in the
     * connection group with the parentID. Otherwise, the root connection group
     * will be used.
     * 
     * @param authToken The authentication token that is used to authenticate
     *                  the user performing the operation.
     * @param parentID The ID of the ConnectionGroup the connections
     *                 belong to. If null, the root connection group will be used.
     * @param connection The connection to create.
     * @return The identifier of the new connection.
     */
    @POST
    public String createConnection(@QueryParam("token") String authToken, 
            @QueryParam("parentID") String parentID, APIConnection connection) {
        UserContext userContext = authenticationService.getUserContextFromAuthToken(authToken);
        
        try {
            if(connection == null)
                throw new GuacamoleClientException("A connection is required for this request.");
            
            // If the parent connection group is passed in, try to find it.
            ConnectionGroup parentConnectionGroup;
            if(parentID == null)
                parentConnectionGroup = userContext.getRootConnectionGroup();
            else {
                ConnectionGroup rootGroup = userContext.getRootConnectionGroup();
                Directory<String, ConnectionGroup> connectionGroupDirectory = rootGroup.getConnectionGroupDirectory();
                parentConnectionGroup = connectionGroupDirectory.get(parentID);
            }
            
            if(parentConnectionGroup == null)
                throw new HTTPException(Status.NOT_FOUND, "No ConnectionGroup found with the provided parentID.");
            
            Directory<String, Connection> connectionDirectory = 
                    parentConnectionGroup.getConnectionDirectory();
            
            // Create the connection
            connectionDirectory.add(new APIConnectionWrapper(connection));
            
            // Return the new connection identifier
            return connection.getIdentifier();
        } catch(GuacamoleSecurityException e) {
            throw new HTTPException(Status.FORBIDDEN, e.getMessage() != null ? e.getMessage() : "Permission denied.");
        } catch(GuacamoleClientException e) {
            throw new HTTPException(Status.BAD_REQUEST, e.getMessage() != null ? e.getMessage() : "Invalid Request.");
        } catch(GuacamoleException e) {
            logger.error("Unexpected GuacamoleException caught while creating connection.", e);
            throw new HTTPException(Status.INTERNAL_SERVER_ERROR, e.getMessage() != null ? e.getMessage() : "Unexpected server error.");
        }
    }
    
    /**
     * Updates a connection.
     * 
     * @param authToken The authentication token that is used to authenticate
     *                  the user performing the operation.
     * @param connectionID The ID of the Connection to move.
     * @param connection The connection to update.
     */
    @POST
    @Path("/{connectionID}")
    public void updateConnection(@QueryParam("token") String authToken, 
            @PathParam("connectionID") String connectionID, APIConnection connection) {
        UserContext userContext = authenticationService.getUserContextFromAuthToken(authToken);
        
        try {
            if(connection == null)
                throw new GuacamoleClientException("A connection is required for this request.");
        
            // Get the connection directory
            ConnectionGroup rootGroup = userContext.getRootConnectionGroup();
            Directory<String, Connection> connectionDirectory =
                    rootGroup.getConnectionDirectory();
            
            // Make sure the connection is there before trying to update
            if(connectionDirectory.get(connectionID) == null)
                throw new HTTPException(Status.NOT_FOUND, "No Connection found with the provided ID.");
            
            // Update the connection
            connectionDirectory.update(new APIConnectionWrapper(connection));
        } catch(GuacamoleSecurityException e) {
            throw new HTTPException(Status.FORBIDDEN, e.getMessage() != null ? e.getMessage() : "Permission denied.");
        } catch(GuacamoleClientException e) {
            throw new HTTPException(Status.BAD_REQUEST, e.getMessage() != null ? e.getMessage() : "Invalid Request.");
        } catch(GuacamoleException e) {
            logger.error("Unexpected GuacamoleException caught updating connection.", e);
            throw new HTTPException(Status.INTERNAL_SERVER_ERROR, e.getMessage() != null ? e.getMessage() : "Unexpected server error.");
        }
    }
    
    /**
     * Moves an individual connection to a different connection group.
     * 
     * @param authToken The authentication token that is used to authenticate
     *                  the user performing the operation.
     * @param connectionID The ID of the Connection to move.
     * @param parentID The ID of the ConnectionGroup the connections
     *                 belong to. If null, the root connection group will be used.
     */
    @PUT
    @Path("/{connectionID}")
    public void moveConnection(@QueryParam("token") String authToken, 
            @PathParam("connectionID") String connectionID, @QueryParam("parentID") String parentID) {
        UserContext userContext = authenticationService.getUserContextFromAuthToken(authToken);
        
        try {
            // Get the connection directory
            ConnectionGroup rootGroup = userContext.getRootConnectionGroup();
            Directory<String, Connection> connectionDirectory =
                    rootGroup.getConnectionDirectory();
            
            // Find the new parent connection group
            Directory<String, ConnectionGroup> connectionGroupDirectory = rootGroup.getConnectionGroupDirectory();
            ConnectionGroup parentConnectionGroup = connectionGroupDirectory.get(parentID);
            
            if(parentConnectionGroup == null)
                throw new HTTPException(Status.NOT_FOUND, "No ConnectionGroup found with the provided parentID.");
            
            // Move the connection
            connectionDirectory.move(connectionID, parentConnectionGroup.getConnectionDirectory());
        } catch(GuacamoleSecurityException e) {
            throw new HTTPException(Status.FORBIDDEN, e.getMessage() != null ? e.getMessage() : "Permission denied.");
        } catch(GuacamoleClientException e) {
            throw new HTTPException(Status.BAD_REQUEST, e.getMessage() != null ? e.getMessage() : "Invalid Request.");
        } catch(GuacamoleException e) {
            logger.error("Unexpected GuacamoleException caught moving connection.", e);
            throw new HTTPException(Status.INTERNAL_SERVER_ERROR, e.getMessage() != null ? e.getMessage() : "Unexpected server error.");
        }
    }

}
