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
package it.greenvulcano.util.crypto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AlgorithmParameters;
import java.security.KeyStore;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import it.greenvulcano.configuration.ConfigurationEvent;
import it.greenvulcano.configuration.ConfigurationListener;
import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.util.metadata.PropertiesHandler;
import it.greenvulcano.util.metadata.PropertiesHandlerException;

/**
 * CryptoHelper class
 * 
 * @version 3.0.0 Feb 17, 2010
 * @author GreenVulcano Developer Team
 * 
 * 
 **/
public final class CryptoHelper implements ConfigurationListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(CryptoHelper.class);
	
    /**
     * CryptoHelper configuration file
     */
    private static final String                CRYPTO_HELPER_FILE   	= "GVCryptoHelper.xml";
    /**
     * CryptoHelper Key Store XPath
     */
    private static final String				   DEFAULT_KEY_STORE_FOLDER	= "/GVCryptoHelper/@key-store-folder";
    /**
     * Keystore id xpath
     */
    private static final String 			   KEY_STORE_ID_XPATH 		= "/GVCryptoHelper/KeyStoreID";    
    /**
     * key id xpath
     */
    private static final String				   KET_ID_XPATH 			= "/GVCryptoHelper/KeyID";
    /**
     * default keyId for configuration cypher key
     */
    public static final String                 DEFAULT_KEY_ID         	= "XMLConfig";
    /**
     * default keyStoreId for configuration cypher key
     */
    public static final String                 DEFAULT_KEYSTORE_ID    	= "GVEsb";
    /**
     * default file name for configuration keystore
     */
    public static final String                 DEFAULT_KEY_STORE_NAME 	= "GVEsb.jks";
    /**
     * default password for configuration keystore
     */
    public static final String                SECRET_KEY_STORE_PWD   	= "__GreenVulcanoPassword__";
    /**
     * default name for configuration cypher key
     */
    public static final String                SECRET_KEY_NAME        	= "XMLConfigKey";
    /**
     * default password for configuration cypher key
     */
    public static final String                SECRET_KEY_PWD         	= "5767Z98e78fs9e46f8x9E3646tHeXcniw4329hnn92nc9";
    /**
     * cache for used keys
     */
    private static HashMap<String, KeyID>      keyIDMap               	= null;
    /**
     * cache for used keyStores
     */
    private static HashMap<String, KeyStoreID> keyStoreIDMap          	= null;
    /**
     * 
     */
    private static String keystorePath 						  		  	= null;    
    /**
     * if true the configuration has changed
     */
    private static boolean                     confChangedFlag        	= true;
    /**
     * singleton reference
     */
    private static CryptoHelper                instance               	= null;

    static {
        init();
    }

    /**
     * Constructor
     */
    private CryptoHelper()
    {
        // do nothing
    }

    /**
     * Check if the given data is already encrypted with the keyID key.
     * 
     * @param keyID
     * @param data
     * @return
     * @throws CryptoHelperException
     * @throws CryptoUtilsException
     */
    public static boolean isEncrypted(String keyID, String data) throws CryptoHelperException, CryptoUtilsException
    {
        KeyID keyid = getKeyID(keyID);
        return (data != null)
                && !data.equals(CryptoUtils.decryptFromBase64(keyid.getFullKeyType(), keyid.getKey(), data, true));
    }


    /**
     * Encrypt the given data with the algorithm of the keyID key. The result
     * is encoded in Base64 and with the type prefix.
     * 
     * @param keyID
     *        the key identification name
     * @param data
     *        the data to encrypt, with encoding 'ISO-8859-1'
     * @param encode
     *        if true the the output is encoded with the type prefix
     * 
     * @return the encrypted data
     * @throws CryptoHelperException
     *         if error occurs
     * @throws CryptoUtilsException
     *         if error occurs
     */
    public static String encrypt(String keyID, String data, boolean encode) throws CryptoHelperException,
          CryptoUtilsException
    {
        return encrypt(keyID, data, CryptoUtils.DEFAULT_STRING_ENCODING, encode, null);
    }

    /**
     * Encrypt the given data with the algorithm of the keyID key. The result
     * is encoded in Base64 and with the type prefix.
     * 
     * @param keyID
     *        the key identification name
     * @param data
     *        the data to encrypt
     * @param inputEnc
     *        the input encoding
     * @param encode
     *        if true the the output is encoded with the type prefix
     * 
     * @return the encrypted data
     * @throws CryptoHelperException
     *         if error occurs
     * @throws CryptoUtilsException
     *         if error occurs
     */
    public static String encrypt(String keyID, String data, String inputEnc, boolean encode,
            AlgorithmParametersHolder pSpecHolder) throws CryptoHelperException, CryptoUtilsException
    {
        KeyID keyid = getKeyID(keyID);
        return CryptoUtils.encryptToBase64(keyid.getFullKeyType(), keyid.getKey(), keyid.getAlgorithmParameters(),
                data, inputEnc, encode, pSpecHolder);
    }

    /**
     * Encrypt the given data with the algorithm of the keyID key. The result
     * is encoded in Base64 and with the type prefix.
     * 
     * @param keyID
     *        the key identification name
     * @param data
     *        the data to encrypt
     * @param inputEnc
     *        the input encoding
     * @param encode
     *        if true the the output is encoded with the type prefix
     * 
     * @return the encrypted data
     * @throws CryptoHelperException
     *         if error occurs
     * @throws CryptoUtilsException
     *         if error occurs
     */
    public static String encrypt(String keyID, AlgorithmParameters params, String data, String inputEnc,
            boolean encode, AlgorithmParametersHolder pSpecHolder) throws CryptoHelperException, CryptoUtilsException
    {
        KeyID keyid = getKeyID(keyID);
        return CryptoUtils.encryptToBase64(keyid.getFullKeyType(), keyid.getKey(), params, data, inputEnc, encode,
                pSpecHolder);
    }

    /**
     * Decrypt the given data with the algorithm of the keyID key. The input
     * must be encoded in Base64 and with the type prefix.
     * 
     * @param keyID
     *        the key identification name
     * @param data
     *        the data to decrypt, with encoding 'ISO-8859-1'
     * @param canBeClear
     *        if true the data can be clear
     * @return the decrypted data
     * @throws CryptoHelperException
     *         if error occurs
     * @throws CryptoUtilsException
     *         if error occurs
     */
    public static String decrypt(String keyID, String data, boolean canBeClear) throws CryptoHelperException,
            CryptoUtilsException
    {
        KeyID keyid = getKeyID(keyID);
        return CryptoUtils.decryptFromBase64(keyid.getFullKeyType(), keyid.getKey(), keyid.getAlgorithmParameters(),
                data, CryptoUtils.DEFAULT_STRING_ENCODING, canBeClear);
    }

    /**
     * Decrypt the given data with the algorithm of the keyID key. The input
     * must be encoded in Base64 and with the type prefix.
     * 
     * @param keyID
     *        the key identification name
     * @param data
     *        the data to decrypt
     * @param inputEnc
     *        the input encoding
     * @param canBeClear
     *        if true the data can be clear
     * @return the decrypted data
     * @throws CryptoHelperException
     *         if error occurs
     * @throws CryptoUtilsException
     *         if error occurs
     */
    public static String decrypt(String keyID, AlgorithmParameters params, String data, String inputEnc,
            boolean canBeClear) throws CryptoHelperException, CryptoUtilsException
    {
        KeyID keyid = getKeyID(keyID);
        return CryptoUtils.decryptFromBase64(keyid.getFullKeyType(), keyid.getKey(), params, data, inputEnc, canBeClear);
    }

    /**
     * Encrypt the given data with the algorithm of the keyID key.
     * 
     * @param keyID
     *        the key identification name
     * @param data
     *        the data to encrypt
     * @param encode
     *        if true the the output is encoded with the type prefix
     * 
     * @return the encrypted data
     * @throws CryptoHelperException
     *         if error occurs
     * @throws CryptoUtilsException
     *         if error occurs
     */
    public static byte[] encrypt(String keyID, byte[] data, boolean encode) throws CryptoHelperException,
            CryptoUtilsException
    {
        KeyID keyid = getKeyID(keyID);
        return CryptoUtils.encrypt(keyid.getFullKeyType(), keyid.getKey(), keyid.getAlgorithmParameters(), data,
                encode, null);
    }

    /**
     * Decrypt the given data with the algorithm of the keyID key. The input
     * must be encoded in Base64.
     * 
     * @param keyID
     *        the key identification name
     * @param data
     *        the data to decrypt
     * @param canBeClear
     *        if true the data can be clear
     * @return the decrypted data
     * @throws CryptoHelperException
     *         if error occurs
     * @throws CryptoUtilsException
     *         if error occurs
     */
    public static byte[] decrypt(String keyID, byte[] data, boolean canBeClear) throws CryptoHelperException,
            CryptoUtilsException
    {
        KeyID keyid = getKeyID(keyID);
        return CryptoUtils.decrypt(keyid.getFullKeyType(), keyid.getKey(), keyid.getAlgorithmParameters(), data,
                canBeClear);
    }

    /**
     * Decrypt the given data with the algorithm of the keyID key. The input
     * must be encoded in Base64.
     * 
     * @param keyID
     *        the key identification name
     * @param data
     *        the data to decrypt
     * @param canBeClear
     *        if true the data can be clear
     * @return the decrypted data
     * @throws CryptoHelperException
     *         if error occurs
     * @throws CryptoUtilsException
     *         if error occurs
     */
    public static byte[] decrypt(String keyID, AlgorithmParameters params, byte[] data, boolean canBeClear)
            throws CryptoHelperException, CryptoUtilsException
    {
        KeyID keyid = getKeyID(keyID);
        return CryptoUtils.decrypt(keyid.getFullKeyType(), keyid.getKey(), params, data, canBeClear);
    }


    /**
     * @param keySid
     */
    public static KeyStore getKeyStore(String keyStoreID) throws CryptoHelperException, KeyStoreUtilsException
    {
        KeyStoreID keySid = getKeyStoreID(keyStoreID);
        
        return KeyStoreUtils.getKeyStore(keystorePath, keySid);
    }

    /**
     *
     */
    public static void resetCache()
    {
        confChangedFlag = true;
    }

    /**
     * Retrieve the requested key
     * 
     * @param kID
     *        the key identification name
     * @return the requested key
     * @throws CryptoHelperException
     *         if error occurs
     */
    public static synchronized KeyID getKeyID(String kID) throws CryptoHelperException
    {
        if (confChangedFlag) {
            loadConfiguration();
        }

        if ((kID == null) || (kID.equals(""))) {
            kID = DEFAULT_KEY_ID;
        }

        KeyID keyid = keyIDMap.get(kID);

        if (keyid == null) {
            throw new CryptoHelperException("CryptoHelper - Error: invalid keyID '" + kID + "'");
        }
        return keyid;
    }

    /**
     * Retrieve the requested keyStore
     * 
     * @param kStoreID
     *        the keyStore identification name
     * @return the requested keyStore
     * @throws CryptoHelperException
     *         if error occurs
     */
    public static synchronized KeyStoreID getKeyStoreID(String kStoreID) throws CryptoHelperException
    {
        if (confChangedFlag) {
            loadConfiguration();
        }

        KeyStoreID keySid = keyStoreIDMap.get(kStoreID);

        if (keySid == null) {
            throw new CryptoHelperException("CryptoHelper - Error: invalid keyStoreID '" + kStoreID + "'");
        }
        return keySid;
    }

    /**
     * Initialise the instance.
     */
    public static void init()
    {
        if (instance == null) {
            instance = new CryptoHelper();
            keyIDMap = new HashMap<String, KeyID>();
            keyStoreIDMap = new HashMap<String, KeyStoreID>();
                 
            loadConfiguration();
            XMLConfig.addConfigurationListener(instance, CRYPTO_HELPER_FILE);
        }
    }

    /**
     * Load the configured KeyIDs.
     */
    private static synchronized void loadConfiguration()
    {
        if (confChangedFlag) {
            KeyStoreUtils.resetCache();
            confChangedFlag = false;
            keyIDMap.clear();
            keyStoreIDMap.clear();
            
            try {
                
            	String path =  XMLConfig.get(CRYPTO_HELPER_FILE, DEFAULT_KEY_STORE_FOLDER, "");          	
            	Path keystoreFolder= Paths.get(PropertiesHandler.expand(path));
            	keystorePath =  Files.isDirectory(keystoreFolder) ? keystoreFolder.toString() : keystoreFolder.getParent().toString();
            			
            	// must be set first because is used by KeyID(Node)
                setDefaultKeyID();
                
                try {
                    NodeList nodeList = XMLConfig.getNodeList(CRYPTO_HELPER_FILE, KEY_STORE_ID_XPATH);
                    if ((nodeList != null) && (nodeList.getLength() > 0)) {
                        for (int i = 0; i < nodeList.getLength(); i++) {
                            try {
                                Node node = nodeList.item(i);
                                KeyStoreID keySid = new KeyStoreID(node);
                                KeyStoreUtils.getKeyStore(keystorePath, keySid);
                                LOG.debug("CryptoHelper - Adding keyStoreID '" + keySid.getKeyStoreID() + "' to cache.");
                                keyStoreIDMap.put(keySid.getKeyStoreID(), keySid);
                            }
                            catch (Exception exc) {
                            	LOG.error("CryptoHelper - Error reading keyStore", exc);
                            }
                        }
                    }
                    
                    nodeList = XMLConfig.getNodeList(CRYPTO_HELPER_FILE, KET_ID_XPATH);
                    if ((nodeList != null) && (nodeList.getLength() > 0)) {
                        for (int i = 0; i < nodeList.getLength(); i++) {
                            try {
                                Node node = nodeList.item(i);
                                KeyID keyid = new KeyID(node);
                                KeyStoreUtils.readKey(keystorePath, keyid);
                                LOG.debug("CryptoHelper - Adding keyID '" + keyid.getKeyID() + "' to cache.");
                                keyIDMap.put(keyid.getKeyID(), keyid);
                            }
                            catch (Exception exc) {
                            	LOG.error("CryptoHelper - Error reading key",exc);
                            }
                        }
                    }
                }
                catch (Exception exc) {
                	LOG.error("CryptoHelper - Error reading file '" + CRYPTO_HELPER_FILE + "' - using only default keyID", exc);                    
                }
            } catch (KeyStoreUtilsException exc) {
            	LOG.error("CryptoHelper Error", exc);                
            } catch (PropertiesHandlerException e) {
            	LOG.error("PropertiesHandler fails to expand value of "+DEFAULT_KEY_STORE_FOLDER , e);
			}  catch (Exception e) {
            	LOG.error("Error in CryptoHelper init " , e);
			}
        }
    }
    	    
    /**
     * 
     * @return
     */
    public static String getKeystorePath() {
    	return keystorePath;
    }

    /**
     * @throws KeyStoreUtilsException
     *         if error occurs
     */
    private static void setDefaultKeyID() throws KeyStoreUtilsException {    	
        if (keyIDMap.containsKey(DEFAULT_KEY_ID)) {
            return;
        }
                
        KeyStoreID keySid = new KeyStoreID(DEFAULT_KEYSTORE_ID, KeyStoreUtils.DEFAULT_KEYSTORE_TYPE, DEFAULT_KEY_STORE_NAME, SECRET_KEY_STORE_PWD, KeyStoreUtils.DEFAULT_KEYSTORE_PROVIDER);
        LOG.debug("CryptoHelper - Adding keyStoreID '" + keySid.getKeyStoreID() + "' to cache.");
        keyStoreIDMap.put(DEFAULT_KEYSTORE_ID, keySid);

        KeyID keyid = new KeyID(DEFAULT_KEY_ID, CryptoUtils.TRIPLE_DES_TYPE, keySid, SECRET_KEY_NAME, SECRET_KEY_PWD);
        keyid.setKey(KeyStoreUtils.readKey(keystorePath, keyid));
        LOG.debug("CryptoHelper - Adding keyID '" + keyid.getKeyID() + "' to cache.");
        keyIDMap.put(DEFAULT_KEY_ID, keyid);       
    }

    /**
     * Obtains events about configuration changes.
     * 
     * @param event
     *        the configuration event
     */
    @Override
    public void configurationChanged(ConfigurationEvent event)
    {
        if ((event.getCode() == ConfigurationEvent.EVT_FILE_REMOVED) && (event.getFile().equals(CRYPTO_HELPER_FILE))) {
            confChangedFlag = true;
        }
    }
}
