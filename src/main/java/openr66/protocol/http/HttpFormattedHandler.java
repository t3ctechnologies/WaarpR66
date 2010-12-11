/**
   This file is part of GoldenGate Project (named also GoldenGate or GG).

   Copyright 2009, Frederic Bregier, and individual contributors by the @author
   tags. See the COPYRIGHT.txt in the distribution for a full listing of
   individual contributors.

   All GoldenGate Project is free software: you can redistribute it and/or 
   modify it under the terms of the GNU General Public License as published 
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   GoldenGate is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with GoldenGate .  If not, see <http://www.gnu.org/licenses/>.
 */
package openr66.protocol.http;

import goldengate.common.exception.FileTransferException;
import goldengate.common.exception.InvalidArgumentException;
import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;
import goldengate.common.utility.GgStringUtils;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import openr66.context.ErrorCode;
import openr66.context.R66Session;
import openr66.context.filesystem.R66Dir;
import openr66.database.DbConstant;
import openr66.database.DbPreparedStatement;
import openr66.database.DbSession;
import openr66.database.data.DbTaskRunner;
import openr66.database.data.AbstractDbData.UpdatedInfo;
import openr66.database.data.DbTaskRunner.TASKSTEP;
import openr66.database.exception.OpenR66DatabaseException;
import openr66.database.exception.OpenR66DatabaseNoConnectionError;
import openr66.database.exception.OpenR66DatabaseSqlError;
import openr66.protocol.configuration.Configuration;
import openr66.protocol.exception.OpenR66Exception;
import openr66.protocol.exception.OpenR66ExceptionTrappedFactory;
import openr66.protocol.exception.OpenR66ProtocolBusinessNoWriteBackException;
import openr66.protocol.localhandler.LocalChannelReference;
import openr66.protocol.utils.FileUtils;
import openr66.protocol.utils.OpenR66SignalHandler;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.handler.traffic.TrafficCounter;

/**
 * Handler for HTTP information support
 *
 * @author Frederic Bregier
 *
 */
public class HttpFormattedHandler extends SimpleChannelUpstreamHandler {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(HttpFormattedHandler.class);

    private static enum REQUEST {
        index("index.html"),
        active("monitoring_header.html","monitoring_end.html"), 
        error("monitoring_header.html","monitoring_end.html"), 
        done("monitoring_header.html","monitoring_end.html"), 
        all("monitoring_header.html","monitoring_end.html"), 
        status("monitoring_header.html","monitoring_end.html");
        
        private String header;
        private String end;
        /**
         * Constructor for a unique file
         * @param uniquefile
         */
        private REQUEST(String uniquefile) {
            this.header = uniquefile;
            this.end = null;
        }
        /**
         * @param header
         * @param end
         */
        private REQUEST(String header, String end) {
            this.header = header;
            this.end = end;
        }
        
        /**
         * Reader for a unique file
         * @return the content of the unique file
         */
        public String readFileUnique(HttpFormattedHandler handler) {
            return handler.readFileHeader(Configuration.configuration.httpBasePath+"monitor/"+this.header);
        }
        
        public String readHeader(HttpFormattedHandler handler) {
            return handler.readFileHeader(Configuration.configuration.httpBasePath+"monitor/"+this.header);
        }
        public String readEnd() {
            return GgStringUtils.readFile(Configuration.configuration.httpBasePath+"monitor/"+this.end);
        }
    }
    
    private static enum REPLACEMENT {
        XXXHOSTIDXXX, XXXLOCACTIVEXXX, XXXNETACTIVEXXX, XXXBANDWIDTHXXX, XXXDATEXXX;
    }
    public static final int LIMITROW = 60;// better if it can be divided by 4

    public final R66Session authentHttp = new R66Session();

    public static final ConcurrentHashMap<String, R66Dir> usedDir = new ConcurrentHashMap<String, R66Dir>();

    private volatile HttpRequest request;

    private final StringBuilder responseContent = new StringBuilder();

    private volatile HttpResponseStatus status;

    private volatile String uriRequest;

    private static final String sINFO = "INFO", sNB = "NB";

    /**
     * The Database connection attached to this NetworkChannel shared among all
     * associated LocalChannels
     */
    private DbSession dbSession;

    /**
     * Does this dbSession is private and so should be closed
     */
    private boolean isPrivateDbSession = false;

    private Map<String, List<String>> params = null;

    private String readFileHeader(String filename) {
        String value;
        try {
            value = GgStringUtils.readFileException(filename);
        } catch (InvalidArgumentException e) {
            logger.error("Error while trying to open: "+filename,e);
            return "";
        } catch (FileTransferException e) {
            logger.error("Error while trying to read: "+filename,e);
            return "";
        }
        StringBuilder builder = new StringBuilder(value);
        
        FileUtils.replace(builder, REPLACEMENT.XXXDATEXXX.toString(),
                (new Date()).toString());
        FileUtils.replace(builder, REPLACEMENT.XXXLOCACTIVEXXX.toString(),
                Integer.toString(
                        Configuration.configuration.getLocalTransaction().
                        getNumberLocalChannel()));
        FileUtils.replace(builder, REPLACEMENT.XXXNETACTIVEXXX.toString(),
                Integer.toString(
                        OpenR66SignalHandler.getNbConnection()));
        FileUtils.replace(builder, REPLACEMENT.XXXHOSTIDXXX.toString(),
                Configuration.configuration.HOST_ID);
        TrafficCounter trafficCounter =
            Configuration.configuration.getGlobalTrafficShapingHandler().getTrafficCounter();
        FileUtils.replace(builder, REPLACEMENT.XXXBANDWIDTHXXX.toString(),
                "IN:"+(trafficCounter.getLastReadThroughput()/131072)+
                "Mbits&nbsp;&nbsp;OUT:"+
                (trafficCounter.getLastWriteThroughput()/131072)+"Mbits");
        return builder.toString();
    }

    private String getTrimValue(String varname) {
        String value = params.get(varname).get(0).trim();
        if (value.length() == 0) {
            value = null;
        }
        return value;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        status = HttpResponseStatus.OK;
        try {
            if (DbConstant.admin.isConnected) {
                this.dbSession = new DbSession(DbConstant.admin, false);
                this.isPrivateDbSession = true;
            }
        } catch (OpenR66DatabaseNoConnectionError e1) {
            // Cannot connect so use default connection
            logger.warn("Use default database connection");
            this.dbSession = DbConstant.admin.session;
        }
        HttpRequest request = this.request = (HttpRequest) e.getMessage();
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request
                .getUri());
        uriRequest = queryStringDecoder.getPath();
        char cval = 'z';
        // check the URI
        if (uriRequest.equalsIgnoreCase("/active")) {
            cval = '0';
        } else if (uriRequest.equalsIgnoreCase("/error")) {
            cval = '1';
        } else if (uriRequest.equalsIgnoreCase("/done")) {
            cval = '2';
        } else if (uriRequest.equalsIgnoreCase("/all")) {
            cval = '3';
        } else if (uriRequest.equalsIgnoreCase("/status")) {
            cval = '4';
        }
        // Get the params according to get or post
        if (request.getMethod() == HttpMethod.GET) {
            params = queryStringDecoder.getParameters();
        } else if (request.getMethod() == HttpMethod.POST) {
            ChannelBuffer content = request.getContent();
            if (content.readable()) {
                String param = content.toString(GgStringUtils.UTF8);
                queryStringDecoder = new QueryStringDecoder("/?" + param);
            } else {
                responseContent.append(REQUEST.index.readFileUnique(this));
                writeResponse(e);
                return;
            }
            params = queryStringDecoder.getParameters();
        }
        int nb = LIMITROW;
        boolean getMenu = (cval == 'z');
        if (!params.isEmpty()) {
            // if not uri, from get or post
            if (getMenu) {
                String info = getTrimValue(sINFO);
                if (info != null) {
                    getMenu = false;
                    cval = info.charAt(0);
                } else {
                    getMenu = true;
                }
            }
            // search the nb param
            String snb = getTrimValue(sNB);
            if (snb != null) {
                nb = Integer.parseInt(snb);
            }
        }
        if (getMenu) {
            responseContent.append(REQUEST.index.readFileUnique(this));
        } else {
            // Use value 0=Active 1=Error 2=Done 3=All
            switch (cval) {
                case '0':
                    active(ctx, nb);
                    break;
                case '1':
                    error(ctx, nb);
                    break;
                case '2':
                    done(ctx, nb);
                    break;
                case '3':
                    all(ctx, nb);
                    break;
                case '4':
                    status(ctx, nb);
                    break;
                default:
                    responseContent.append(REQUEST.index.readFileUnique(this));
            }
        }
        writeResponse(e);
    }

    /**
     * Add all runners from preparedStatement for type
     *
     * @param preparedStatement
     * @param type
     * @param nb
     * @throws OpenR66DatabaseNoConnectionError
     * @throws OpenR66DatabaseSqlError
     */
    private void addRunners(DbPreparedStatement preparedStatement, String type,
            int nb) throws OpenR66DatabaseNoConnectionError,
            OpenR66DatabaseSqlError {
        try {
            preparedStatement.executeQuery();
            responseContent
                    .append("<style>td{font-size: 8pt;}</style><table border=\"2\">");
            responseContent.append("<tr><td>");
            responseContent.append(type);
            responseContent.append("</td>");
            responseContent.append(DbTaskRunner.headerHtml());
            responseContent.append("</tr>\r\n");
            int i = 0;
            while (preparedStatement.getNext()) {
                DbTaskRunner taskRunner = DbTaskRunner
                        .getFromStatement(preparedStatement);
                responseContent.append("<tr><td>");
                responseContent.append(taskRunner.isSender()? "S" : "R");
                responseContent.append("</td>");
                LocalChannelReference lcr =
                    Configuration.configuration.getLocalTransaction().
                    getFromRequest(taskRunner.getKey());
                responseContent.append(taskRunner.toHtml(authentHttp,
                        lcr != null ? "Active" : "NotActive"));
                responseContent.append("</tr>\r\n");
                if (nb > 0) {
                    i ++;
                    if (i >= nb) {
                        break;
                    }
                }
            }
            responseContent.append("</table><br>\r\n");
        } finally {
            if (preparedStatement != null) {
                preparedStatement.realClose();
            }
        }
    }

    /**
     * print all active transfers
     *
     * @param ctx
     * @param nb
     */
    private void active(ChannelHandlerContext ctx, int nb) {
        responseContent.append(REQUEST.active.readHeader(this));
        DbPreparedStatement preparedStatement = null;
        try {
            preparedStatement = DbTaskRunner.getStatusPrepareStament(dbSession,
                    ErrorCode.Running, nb);
            addRunners(preparedStatement, ErrorCode.Running.mesg, nb);
            preparedStatement = DbTaskRunner.getUpdatedPrepareStament(
                    dbSession, UpdatedInfo.INTERRUPTED, true, nb);
            addRunners(preparedStatement, UpdatedInfo.INTERRUPTED.name(), nb);
            preparedStatement = DbTaskRunner.getUpdatedPrepareStament(
                    dbSession, UpdatedInfo.TOSUBMIT, true, nb);
            addRunners(preparedStatement, UpdatedInfo.TOSUBMIT.name(), nb);
            preparedStatement = DbTaskRunner.getStatusPrepareStament(dbSession,
                    ErrorCode.InitOk, nb);
            addRunners(preparedStatement, ErrorCode.InitOk.mesg, nb);
            preparedStatement = DbTaskRunner.getStatusPrepareStament(dbSession,
                    ErrorCode.PreProcessingOk, nb);
            addRunners(preparedStatement, ErrorCode.PreProcessingOk.mesg, nb);
            preparedStatement = DbTaskRunner.getStatusPrepareStament(dbSession,
                    ErrorCode.TransferOk, nb);
            addRunners(preparedStatement, ErrorCode.TransferOk.mesg, nb);
            preparedStatement = DbTaskRunner.getStatusPrepareStament(dbSession,
                    ErrorCode.PostProcessingOk, nb);
            addRunners(preparedStatement, ErrorCode.PostProcessingOk.mesg, nb);
            preparedStatement = null;
        } catch (OpenR66DatabaseException e) {
            if (preparedStatement != null) {
                preparedStatement.realClose();
            }
            logger.warn("OpenR66 Web Error {}", e.getMessage());
            sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE);
            return;
        }
        responseContent.append(REQUEST.active.readEnd());
    }

    /**
     * print all transfers in error
     *
     * @param ctx
     * @param nb
     */
    private void error(ChannelHandlerContext ctx, int nb) {
        responseContent.append(REQUEST.error.readHeader(this));
        DbPreparedStatement preparedStatement = null;
        try {
            preparedStatement = DbTaskRunner.getUpdatedPrepareStament(
                    dbSession, UpdatedInfo.INERROR, true, nb / 2);
            addRunners(preparedStatement, UpdatedInfo.INERROR.name(), nb / 2);
            preparedStatement = DbTaskRunner.getUpdatedPrepareStament(
                    dbSession, UpdatedInfo.INTERRUPTED, true, nb / 2);
            addRunners(preparedStatement, UpdatedInfo.INTERRUPTED.name(),
                    nb / 2);
            preparedStatement = DbTaskRunner.getStepPrepareStament(dbSession,
                    TASKSTEP.ERRORTASK, nb / 4);
            addRunners(preparedStatement, TASKSTEP.ERRORTASK.name(), nb / 4);
        } catch (OpenR66DatabaseException e) {
            if (preparedStatement != null) {
                preparedStatement.realClose();
            }
            logger.warn("OpenR66 Web Error {}", e.getMessage());
            sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE);
            return;
        }
        responseContent.append(REQUEST.error.readEnd());
    }

    /**
     * Print all done transfers
     *
     * @param ctx
     * @param nb
     */
    private void done(ChannelHandlerContext ctx, int nb) {
        responseContent.append(REQUEST.done.readHeader(this));
        DbPreparedStatement preparedStatement = null;
        try {
            preparedStatement = DbTaskRunner.getStatusPrepareStament(dbSession,
                    ErrorCode.CompleteOk, nb);
            addRunners(preparedStatement, ErrorCode.CompleteOk.mesg, nb);
        } catch (OpenR66DatabaseException e) {
            if (preparedStatement != null) {
                preparedStatement.realClose();
            }
            logger.warn("OpenR66 Web Error {}", e.getMessage());
            sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE);
            return;
        }
        responseContent.append(REQUEST.done.readEnd());
    }

    /**
     * Print all nb last transfers
     *
     * @param ctx
     * @param nb
     */
    private void all(ChannelHandlerContext ctx, int nb) {
        responseContent.append(REQUEST.all.readHeader(this));
        DbPreparedStatement preparedStatement = null;
        try {
            preparedStatement = DbTaskRunner.getStatusPrepareStament(dbSession,
                    null, nb);// means all
            addRunners(preparedStatement, "ALL RUNNERS: " + nb, nb);
        } catch (OpenR66DatabaseException e) {
            if (preparedStatement != null) {
                preparedStatement.realClose();
            }
            logger.warn("OpenR66 Web Error {}", e.getMessage());
            sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE);
            return;
        }
        responseContent.append(REQUEST.all.readEnd());
    }


    /**
     * print only status
     *
     * @param ctx
     * @param nb
     */
    private void status(ChannelHandlerContext ctx, int nb) {
        responseContent.append(REQUEST.status.readHeader(this));
        DbPreparedStatement preparedStatement = null;
        try {
            preparedStatement = DbTaskRunner.getUpdatedPrepareStament(
                    dbSession, UpdatedInfo.INERROR, true, 1);
            try {
                preparedStatement.executeQuery();
                if (preparedStatement.getNext()) {
                    responseContent.append("<p>Some Transfers are in ERROR</p><br>");
                    status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                }
            } finally {
                if (preparedStatement != null) {
                    preparedStatement.realClose();
                }
            }
            preparedStatement = DbTaskRunner.getUpdatedPrepareStament(
                    dbSession, UpdatedInfo.INTERRUPTED, true, 1);
            try {
                preparedStatement.executeQuery();
                if (preparedStatement.getNext()) {
                    responseContent.append("<p>Some Transfers are INTERRUPTED</p><br>");
                    status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                }
            } finally {
                if (preparedStatement != null) {
                    preparedStatement.realClose();
                }
            }
            preparedStatement = DbTaskRunner.getStepPrepareStament(dbSession,
                    TASKSTEP.ERRORTASK, 1);
            try {
                preparedStatement.executeQuery();
                if (preparedStatement.getNext()) {
                    responseContent.append("<p>Some Transfers are in ERRORTASK</p><br>");
                    status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                }
            } finally {
                if (preparedStatement != null) {
                    preparedStatement.realClose();
                }
            }
            if (status != HttpResponseStatus.INTERNAL_SERVER_ERROR) {
                responseContent.append("<p>No problem is found in Transfers</p><br>");
            }
        } catch (OpenR66DatabaseException e) {
            if (preparedStatement != null) {
                preparedStatement.realClose();
            }
            logger.warn("OpenR66 Web Error {}", e.getMessage());
            sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE);
            return;
        }
        responseContent.append(REQUEST.status.readEnd());
    }

    /**
     * Write the response
     *
     * @param e
     */
    private void writeResponse(MessageEvent e) {
        // Convert the response content to a ChannelBuffer.
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(responseContent
                .toString(), GgStringUtils.UTF8);
        responseContent.setLength(0);
        // Decide whether to close the connection or not.
        boolean keepAlive = HttpHeaders.isKeepAlive(request);
        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request
                .getHeader(HttpHeaders.Names.CONNECTION)) ||
                (!keepAlive);

        // Build the response object.
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                status);
        response.setContent(buf);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html");
        if (keepAlive) {
            response.setHeader(HttpHeaders.Names.CONNECTION,
                    HttpHeaders.Values.KEEP_ALIVE);
        }
        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String
                    .valueOf(buf.readableBytes()));
        }

        String cookieString = request.getHeader(HttpHeaders.Names.COOKIE);
        if (cookieString != null) {
            CookieDecoder cookieDecoder = new CookieDecoder();
            Set<Cookie> cookies = cookieDecoder.decode(cookieString);
            if (!cookies.isEmpty()) {
                // Reset the cookies if necessary.
                CookieEncoder cookieEncoder = new CookieEncoder(true);
                for (Cookie cookie: cookies) {
                    cookieEncoder.addCookie(cookie);
                }
                response.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder
                        .encode());
            }
        }

        // Write the response.
        ChannelFuture future = e.getChannel().write(response);

        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
        if (this.isPrivateDbSession && dbSession != null) {
            dbSession.disconnect();
            dbSession = null;
        }
    }

    /**
     * Send an error and close
     *
     * @param ctx
     * @param status
     */
    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                status);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html");
        responseContent.setLength(0);
        responseContent.append(REQUEST.error.readHeader(this));
        responseContent.append("OpenR66 Web Failure: ");
        responseContent.append(status.toString());
        responseContent.append(REQUEST.error.readEnd());
        response.setContent(ChannelBuffers.copiedBuffer(responseContent
                .toString(), GgStringUtils.UTF8));
        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(
                ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        OpenR66Exception exception = OpenR66ExceptionTrappedFactory
                .getExceptionFromTrappedException(e.getChannel(), e);
        if (exception != null) {
            if (!(exception instanceof OpenR66ProtocolBusinessNoWriteBackException)) {
                if (e.getCause() instanceof IOException) {
                    if (this.isPrivateDbSession && dbSession != null) {
                        dbSession.disconnect();
                        dbSession = null;
                    }
                    // Nothing to do
                    return;
                }
                logger.warn("Exception in HttpHandler {}", exception.getMessage());
            }
            if (e.getChannel().isConnected()) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            }
        } else {
            if (this.isPrivateDbSession && dbSession != null) {
                dbSession.disconnect();
                dbSession = null;
            }
            // Nothing to do
            return;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.jboss.netty.channel.SimpleChannelUpstreamHandler#channelClosed(org
     * .jboss.netty.channel.ChannelHandlerContext,
     * org.jboss.netty.channel.ChannelStateEvent)
     */
    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        super.channelClosed(ctx, e);
        if (this.isPrivateDbSession && dbSession != null) {
            dbSession.disconnect();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.jboss.netty.channel.SimpleChannelUpstreamHandler#channelConnected
     * (org.jboss.netty.channel.ChannelHandlerContext,
     * org.jboss.netty.channel.ChannelStateEvent)
     */
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        authentHttp.getAuth().specialNoSessionAuth(false, Configuration.configuration.HOST_ID);
        super.channelConnected(ctx, e);
        ChannelGroup group = Configuration.configuration.getHttpChannelGroup();
        if (group != null) {
            group.add(e.getChannel());
        }
    }
}