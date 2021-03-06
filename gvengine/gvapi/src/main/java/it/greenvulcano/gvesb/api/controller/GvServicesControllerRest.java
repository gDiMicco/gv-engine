/*******************************************************************************
 * Copyright (c) 2009, 2016 GreenVulcano ESB Open Source Project.
 * All rights reserved.
 *
 * This file is part of GreenVulcano ESB.
 *
 * GreenVulcano ESB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GreenVulcano ESB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GreenVulcano ESB. If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package it.greenvulcano.gvesb.api.controller;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.cxf.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.configuration.XMLConfigException;
import it.greenvulcano.gvesb.api.dto.OperationDTO;
import it.greenvulcano.gvesb.api.dto.ServiceDTO;
import it.greenvulcano.gvesb.api.security.GVSecurityContext;
import it.greenvulcano.gvesb.api.security.JaxRsIdentityInfo;
import it.greenvulcano.gvesb.buffer.GVBuffer;
import it.greenvulcano.gvesb.buffer.GVException;
import it.greenvulcano.gvesb.buffer.GVPublicException;
import it.greenvulcano.gvesb.core.jmx.OperationInfo;
import it.greenvulcano.gvesb.core.jmx.ServiceOperationInfo;
import it.greenvulcano.gvesb.core.jmx.ServiceOperationInfoManager;
import it.greenvulcano.gvesb.core.pool.GreenVulcanoPool;
import it.greenvulcano.gvesb.core.pool.GreenVulcanoPoolException;
import it.greenvulcano.gvesb.core.pool.GreenVulcanoPoolManager;
import it.greenvulcano.gvesb.identity.GVIdentityHelper;

@CrossOriginResourceSharing(allowAllOrigins=true, allowCredentials=true, exposeHeaders={"Content-type", "Content-Range", "X-Auth-Status"})
public class GvServicesControllerRest extends BaseControllerRest {
	private final static Logger LOG = LoggerFactory.getLogger(GvServicesControllerRest.class);
		
	@Path("/")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getServices() {
		
		String response = null;
		try {
			 
			NodeList serviceNodes = XMLConfig.getNodeList("GVServices.xml", "//Service");
			
			
			Map<String, ServiceDTO> services = IntStream.range(0, serviceNodes.getLength())
							 .mapToObj(serviceNodes::item)
							 .map(this::buildServiceFromConfig)
							 .filter(Optional::isPresent)
							 .map(Optional::get)
							 .collect(Collectors.toMap(ServiceDTO::getIdService, Function.identity()));
			
			LOG.debug("Services found "+serviceNodes.getLength());
			response = toJson(services);
		} catch (XMLConfigException | JsonProcessingException xmlConfigException){
			LOG.error("Error reading services configuration", xmlConfigException);
			throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(toJson(xmlConfigException)).build());
		}	
		
		return Response.ok(response).build();
		
	}
	
	@Path("/{service}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getOperations(@PathParam("service") String service) {
	
		if("probe".equals(service)) {
			return Response.ok("It works").header("Content-Type", "text/plain; charset=utf-8").build();
		}
		
		if("saymyname".equals(service)) {
			String logo = "ICAgIC4tLS0tLS0tLgogICAgfCAgICAgICB8CiAtPV9fX19fX19fX19fPS0KICAgX19fXyAgIF9fX18KICB8X19fXyk9KF9fX198CgogICAgICAgIyMjCiAgICAgICMgPSAjCiAgICAgICMjIyMjCiAgICAgICAjIyMKCiAgICBHViBFU0IgdjQK";
						
			return Response.ok("\n" + new String(Base64.getDecoder().decode(logo)))
						   .header("Content-Type", "text/plain; charset=utf-8")
						   .build();
		}
		
		String response = null;		
		
		try {
			
			Node serviceNode = Optional.ofNullable(XMLConfig.getNode("GVServices.xml", "//Service[@id-service='"+service+"']"))
									   .orElseThrow(NoSuchElementException::new);
			
			ServiceDTO svc = buildServiceFromConfig(serviceNode).orElseThrow(NoSuchElementException::new);;
		    response = toJson(svc);		   
			
		} catch (NoSuchElementException noSuchElementException) {
			throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity("Service not found").build());
		} catch (XMLConfigException | JsonProcessingException xmlConfigException) {
			throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(toJson(xmlConfigException)).build());
		}		
		
		return Response.ok(response).build();
	}
	
	
	
	@Path("/{service}/{operation}")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)	
	public Response execute(@Context MessageContext jaxrsContext, @PathParam("service") String service, @PathParam("operation")String operation, String data) {
		return runOperation(jaxrsContext, service, operation, data);
	}
		
	@Path("/{service}/{operation}")
	@GET	
	@Produces(MediaType.APPLICATION_JSON)	
	public Response query(@Context MessageContext jaxrsContext, @PathParam("service") String service, @PathParam("operation")String operation, String data) {
		return runOperation(jaxrsContext, service, operation, data);
	}

	@Path("/{service}/{operation}")
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)	
	public Response modify(@Context MessageContext jaxrsContext, @PathParam("service") String service, @PathParam("operation")String operation, String data) {
		return runOperation(jaxrsContext, service, operation, data);
	}

	@Path("/{service}/{operation}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public Response drop(@Context MessageContext jaxrsContext, @PathParam("service") String service, @PathParam("operation")String operation, String data) {
		return runOperation(jaxrsContext, service, operation, data);
	}
			
	private Response runOperation(MessageContext jaxrsContext, String service, String operation, String data ) {

		SecurityContext securityContext = JAXRSUtils.getCurrentMessage().get(SecurityContext.class);
		if (securityContext instanceof GVSecurityContext) {
			GVIdentityHelper.push(new JaxRsIdentityInfo(jaxrsContext.getSecurityContext(), GVSecurityContext.class.cast(securityContext).getIdentity(), jaxrsContext.getHttpServletRequest().getRemoteAddr()));
	    }
		String response = null;		
		GVBuffer input = null;
		try {
			input = new GVBuffer();
			
			for (Entry<String, List<String>> prop : jaxrsContext.getUriInfo().getQueryParameters().entrySet()){
				input.setProperty(prop.getKey(), prop.getValue().stream().collect(Collectors.joining(";")));
			}
			
			input.setService(service);
			input.setObject(data);
		} catch (GVException e) {
			LOG.error("gvcoreapi - Error building GVBuffer", e);
			throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("GVBuffer creation failed").build());
		}
		
		
		GreenVulcanoPool gvpoolInstance = null;
		try {
			gvpoolInstance = GreenVulcanoPoolManager.instance().getGreenVulcanoPool("gvapi").orElseGet(GreenVulcanoPoolManager::getDefaultGreenVulcanoPool);
		} catch (Exception e) {
			LOG.error("gvcoreapi - Error retriving a GreenVulcanoPool instance for subsystem gvapi", e);						
			throw new WebApplicationException(Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("GreenVulcanoPool not available for subsystem gvapi").build());
		}
				
		try {
			
			GVBuffer output = gvpoolInstance.forward(input, operation);
			if (output.getObject() instanceof String) {
				response = output.getObject().toString();
			} else if (output.getObject() instanceof org.json.JSONObject) {
				response = org.json.JSONObject.class.cast(output.getObject()).toString();
			}  else if (output.getObject() instanceof org.json.JSONArray) {
				response = org.json.JSONArray.class.cast(output.getObject()).toString();
			} else if (output.getObject() instanceof byte[]) {
				String encoding = output.getProperty("OBJECT_ENCODING");
				
				try {
					if (encoding!=null) {
						response = new String((byte[]) output.getObject(), encoding);
					} else {
						throw new UnsupportedEncodingException();
					}
				} catch (UnsupportedEncodingException e) {
					response = "{ \"base64\": \"" + Base64.getEncoder().encodeToString((byte[]) output.getObject()) +"\"}";
				}
				
			} else if (Objects.nonNull(output.getObject())) {
				
				response = toJson(output.getObject());
								
			} else {
				return Response.ok().build();
			}
			
		
		} catch (GVPublicException e) {			
			LOG.error("gvcoreapi - Error performing operation "+operation+" on "+service+" service", e);
			
			if (e.getMessage().contains("GV_SERVICE_NOT_FOUND_ERROR")){
				throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity(toJson(e)).build());
			} else if (e.getMessage().contains("GVCORE_BAD_GVOPERATION_NAME_ERROR")) {
				throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity(toJson(e)).build());
			}  else if (e.getMessage().contains("GV_SERVICE_POLICY_ERROR")) {
				throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).entity(toJson(e)).build());
			}
			
			throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(toJson(e)).build());
		
		} catch (GreenVulcanoPoolException e) {
			LOG.error("gvcoreapi - Error performing forward on GreenVulcanoPool instance "+  gvpoolInstance.getSubsystem(), e);
			throw new WebApplicationException(Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(toJson(e)).build());			
		
		} catch (JsonProcessingException e) {
			LOG.error("gvcoreapi - Unparsable response data", e);
			throw new WebApplicationException(Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).entity(toJson(e)).build());			
		}
		
		return Response.ok(response).build();
	}	
		
	private Optional<ServiceDTO> buildServiceFromConfig(Node config) {
		
		
		
		try {
			ServiceDTO service = new ServiceDTO(XMLConfig.get(config, "@id-service"), 
										  XMLConfig.get(config, "@group-name"), 
										  XMLConfig.get(config, "@service-activation").equals("on"), 
										  XMLConfig.get(config, "@statistics").equals("on"));
						
			NodeList operationNodes = XMLConfig.getNodeList(config, "./Operation");
			
			
			ServiceOperationInfo serviceInfo = null;
			try {
				serviceInfo = ServiceOperationInfoManager.instance().getServiceOperationInfo(service.getIdService(), false);
			
			} catch (Exception e) {
				LOG.warn("Fail to retrieve ServiceOperationInfo MBean for service "+service, e);
			}
			
			final Optional<ServiceOperationInfo> serviceOperationInfo = Optional.ofNullable(serviceInfo);
			
			IntStream.range(0, operationNodes.getLength())
			  .mapToObj(operationNodes::item)
			  .map(node -> buildOperationFromConfig(node,  serviceOperationInfo))
			  .filter(Optional::isPresent)
			  .map(Optional::get).forEach( op -> service.getOperations().put(op.getName(), op) );
			
			
						
			return Optional.of(service);
		} catch (NullPointerException|XMLConfigException xmlConfigException){
			LOG.error("Error reading service configuration", xmlConfigException);
		}
		
		return Optional.empty();
		
	}
			
	private Optional<OperationDTO> buildOperationFromConfig(Node config, Optional<ServiceOperationInfo> serviceOperationInfo) {
		try {
			
			String operationName = XMLConfig.get(config, "@forward-name", XMLConfig.get(config, "@name") );
			
			OperationDTO operation = null;			
			
			try {
				OperationInfo operationInfo = serviceOperationInfo.get().getOperationInfo(operationName, false) ;
				operation = new OperationDTO(operationInfo.getOperation(), operationInfo.getOperationActivation(), operationInfo.getTotalSuccess(), operationInfo.getTotalFailure());
								
			} catch (Exception e) {
				operation = new OperationDTO( operationName,  XMLConfig.get(config, "@operation-activation").equals("on")) ;
			}											
			
			return Optional.of(operation);
			
		} catch (NullPointerException|XMLConfigException xmlConfigException){
			LOG.error("Error reading operation configuration", xmlConfigException);
		}
		
		return Optional.empty();
		
	}	
	
}