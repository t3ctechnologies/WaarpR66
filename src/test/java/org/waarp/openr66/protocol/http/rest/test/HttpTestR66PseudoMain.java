/**
   This file is part of Waarp Project.

   Copyright 2009, Frederic Bregier, and individual contributors by the @author
   tags. See the COPYRIGHT.txt in the distribution for a full listing of
   individual contributors.

   All Waarp Project is free software: you can redistribute it and/or 
   modify it under the terms of the GNU General Public License as published 
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Waarp is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with Waarp .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.waarp.openr66.protocol.http.rest.test;

import java.io.File;
import java.io.IOException;

import org.waarp.common.exception.CryptoException;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.logging.WaarpSlf4JLoggerFactory;
import org.waarp.gateway.kernel.rest.RestConfiguration;
import org.waarp.openr66.protocol.http.rest.HttpRestR66Handler;
import org.waarp.openr66.protocol.http.rest.HttpRestR66Handler.RESTHANDLERS;
import org.waarp.openr66.server.R66Server;

/**
 * @author "Frederic Bregier"
 *
 */
public class HttpTestR66PseudoMain {

    public static RestConfiguration config;

    public static RestConfiguration getTestConfiguration() throws CryptoException, IOException {
        RestConfiguration configuration = new RestConfiguration();
        configuration.REST_PORT = 8088;
        configuration.REST_SSL = false;
        configuration.RESTHANDLERS_CRUD = new byte[RESTHANDLERS.values().length];
        for (int i = 0; i < configuration.RESTHANDLERS_CRUD.length; i++) {
            configuration.RESTHANDLERS_CRUD[i] = RestConfiguration.CRUD.ALL.mask;
        }
        configuration.REST_AUTHENTICATED = true;
        configuration.initializeKey(new File("/opt/R66/certs/key.sha256"));
        configuration.REST_TIME_LIMIT = 10000;
        configuration.REST_SIGNATURE = true;
        configuration.REST_ADDRESS = "127.0.0.1";
        return configuration;
    }

    public static RestConfiguration getTestConfiguration2() throws CryptoException, IOException {
        RestConfiguration configuration = new RestConfiguration();
        configuration.REST_PORT = 8089;
        configuration.REST_SSL = false;
        configuration.RESTHANDLERS_CRUD = new byte[RESTHANDLERS.values().length];
        for (int i = 0; i < configuration.RESTHANDLERS_CRUD.length; i++) {
            configuration.RESTHANDLERS_CRUD[i] = RestConfiguration.CRUD.READ.mask;
        }
        configuration.REST_AUTHENTICATED = false;
        configuration.REST_TIME_LIMIT = 100000;
        configuration.REST_SIGNATURE = false;
        configuration.REST_ADDRESS = "127.0.0.1";
        return configuration;
    }

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        WaarpLoggerFactory.setDefaultFactory(new WaarpSlf4JLoggerFactory(null));
        final WaarpLogger logger = WaarpLoggerFactory
                .getLogger(HttpTestR66PseudoMain.class);
        String pathTemp = "/tmp";
        if (!R66Server.initialize(args[0])) {
            System.err.println("Error during startup");
            System.exit(1);
        }

        config = getTestConfiguration();
        HttpRestR66Handler.initialize(pathTemp);
        HttpRestR66Handler.initializeService(config);

        logger.warn("Server RestOpenR66 starts");
        /* HmacSha256 sha = new HmacSha256();
        sha.generateKey();
        sha.saveSecretKey(new File("J:/Temp/temp/key.sha256"));
        */
    }

}
