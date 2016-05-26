/*
 * Copyright (c) 2009-2010 GreenVulcano ESB Open Source Project. All rights
 * reserved.
 *
 * This file is part of GreenVulcano ESB.
 *
 * GreenVulcano ESB is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * GreenVulcano ESB is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GreenVulcano ESB. If not, see <http://www.gnu.org/licenses/>.
 */
package it.greenvulcano.gvesb.virtual.internal;

import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.configuration.XMLConfigException;
import it.greenvulcano.gvesb.buffer.GVBuffer;
import it.greenvulcano.gvesb.buffer.Id;
import it.greenvulcano.gvesb.virtual.AcknowledgeException;
import it.greenvulcano.gvesb.virtual.ConnectionException;
import it.greenvulcano.gvesb.virtual.DequeueException;
import it.greenvulcano.gvesb.virtual.DequeueOperation;
import it.greenvulcano.gvesb.virtual.FilterException;
import it.greenvulcano.gvesb.virtual.InitializationException;
import it.greenvulcano.gvesb.virtual.InvalidDataException;
import it.greenvulcano.gvesb.virtual.OperationFactory;
import it.greenvulcano.gvesb.virtual.OperationKey;
import it.greenvulcano.gvesb.virtual.VCLOperationKey;
import it.greenvulcano.util.xpath.XPathFinder;

import org.w3c.dom.Node;

/**
 *
 * @version 3.0.0 14/lug/2010
 * @author GreenVulcano Developer Team
 */
public class ProxyDequeueOperation implements DequeueOperation
{
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ProxyDequeueOperation.class);

    private String              idSystem;
    private String              idChannel;
    private String              vmOpName;
    private DequeueOperation    operation;
    private OperationKey        key;

    /**
     * @see it.greenvulcano.gvesb.virtual.Operation#init(org.w3c.dom.Node)
     */
    @Override
    public void init(Node node) throws InitializationException
    {
        try {
            idSystem = XMLConfig.get(node, "@id-system");
            idChannel = XMLConfig.get(node, "@id-channel");
            vmOpName = XMLConfig.get(node, "@operation");

            logger.debug("Dequeue Proxy to: " + idSystem + "#" + idChannel + "#" + vmOpName);

            String xPath = "/GVSystems/Systems/System[@id-system='" + idSystem + "']/Channel[@id-channel='" + idChannel
                    + "']/*[@name='" + vmOpName + "' and @type='dequeue']";
            Node opNode = null;
            try {
                opNode = XMLConfig.getNode("GVSystems.xml", xPath);
            }
            catch (XMLConfigException exc) {
                throw new InitializationException("GVVCL_PROXYCALL_SEARCH_ERROR", new String[][]{
                        {"node", XPathFinder.buildXPath(node)}, {"xpath", xPath}});
            }
            if (opNode == null) {
                throw new InitializationException("GVVCL_PROXYCALL_SEARCH_ERROR", new String[][]{
                        {"node", XPathFinder.buildXPath(node)}, {"xpath", xPath}});
            }
            OperationKey opKey = new VCLOperationKey(opNode);

            operation = OperationFactory.createDequeue(opKey);
        }
        catch (InitializationException exc) {
            throw exc;
        }
        catch (Exception exc) {
            throw new InitializationException("GVVCL_PROXYCALL_INIT_ERROR",
                    new String[][]{{"node", node.getLocalName()}}, exc);
        }
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.DequeueOperation#perform(it.greenvulcano.gvesb.buffer.GVBuffer)
     */
    @Override
    public GVBuffer perform(GVBuffer gvBuffer) throws ConnectionException, DequeueException, InvalidDataException, InterruptedException
    {
        return operation.perform(gvBuffer);
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.DequeueOperation#acknowledge(it.greenvulcano.gvesb.buffer.Id)
     */
    @Override
    public void acknowledge(Id id) throws ConnectionException, AcknowledgeException
    {
        operation.acknowledge(id);
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.DequeueOperation#acknowledgeAll()
     */
    @Override
    public void acknowledgeAll() throws ConnectionException, AcknowledgeException
    {
        operation.acknowledgeAll();
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.DequeueOperation#rollback(it.greenvulcano.gvesb.buffer.Id)
     */
    @Override
    public void rollback(Id id) throws ConnectionException, AcknowledgeException
    {
        operation.rollback(id);
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.DequeueOperation#rollbackAll()
     */
    @Override
    public void rollbackAll() throws ConnectionException, AcknowledgeException
    {
        operation.rollbackAll();
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.DequeueOperation#setFilter(java.lang.String)
     */
    @Override
    public void setFilter(String filter) throws FilterException
    {
        operation.setFilter(filter);
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.DequeueOperation#setTimeout(long)
     */
    @Override
    public void setTimeout(long timeout)
    {
        operation.setTimeout(timeout);
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.Operation#cleanUp()
     */
    @Override
    public void cleanUp()
    {
        operation.cleanUp();
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.Operation#destroy()
     */
    @Override
    public void destroy()
    {
        operation.destroy();
        operation = null;
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.Operation#setKey(it.greenvulcano.gvesb.virtual.OperationKey)
     */
    public void setKey(OperationKey key)
    {
        this.key = key;
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.Operation#getKey()
     */
    public OperationKey getKey()
    {
        return key;
    }

    /**
     * Return the alias for the given service
     *
     * @param gvBuffer
     *        the input service data
     * @return the configured alias
     */
    public String getServiceAlias(GVBuffer gvBuffer)
    {
        return gvBuffer.getService();
    }

}
