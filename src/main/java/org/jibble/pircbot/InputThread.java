/* 
Copyright Paul James Mutton, 2001-2009, http://www.jibble.org/

This file is part of PircBot.

This software is dual-licensed, allowing you to choose between the GNU
General Public License (GPL) and the www.jibble.org Commercial License.
Since the GPL may be too restrictive for use in a proprietary application,
a commercial license is also provided. Full license information can be
found at http://www.jibble.org/licenses/

*/


package org.jibble.pircbot;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.nio.charset.Charset;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Thread which reads lines from the IRC server.  It then
 * passes these lines to the PircBot without changing them.
 * This running Thread also detects disconnection from the server
 * and is thus used by the OutputThread to send lines to the server.
 *
 * @author  Paul James Mutton,
 *          <a href="http://www.jibble.org/">http://www.jibble.org/</a>
 * @version    1.5.0 (Build time: Mon Dec 14 20:07:17 2009)
 */
public class InputThread extends Thread {

    private static Logger logger = LoggerFactory.getLogger(InputThread.class);
    
    /**
     * The InputThread reads lines from the IRC server and allows the
     * PircBot to handle them.
     *
     * @param bot An instance of the underlying PircBot.
     * @param stream The InputStream that reads bytes from the server.
     * @param bwriter The BufferedWriter that sends lines to the server.
     */
    InputThread(PircBot bot, Socket socket, InputStream stream, BufferedWriter bwriter, String encoding) {
        _bot = bot;
        _socket = socket;
        _stream = stream;
        _bwriter = bwriter;
        _encoding = encoding;
        this.setName(this.getClass() + "-Thread");
    }
    
    
    /**
     * Sends a raw line to the IRC server as soon as possible, bypassing the
     * outgoing message queue.
     *
     * @param line The raw line to send to the IRC server.
     */
    void sendRawLine(String line) {
        OutputThread.sendRawLine(_bot, _bwriter, line);
    }
    
    
    /**
     * Returns true if this InputThread is connected to an IRC server.
     * The result of this method should only act as a rough guide,
     * as the result may not be valid by the time you act upon it.
     * 
     * @return True if still connected.
     */
    boolean isConnected() {
        return _isConnected;
    }
    
    
    /**
     * Called to start this Thread reading lines from the IRC server.
     * When a line is read, this method calls the handleLine method
     * in the PircBot, which may subsequently call an 'onXxx' method
     * in the PircBot subclass.  If any subclass of Throwable (i.e.
     * any Exception or Error) is thrown by your method, then this
     * method will print the stack trace to the standard output.  It
     * is probable that the PircBot may still be functioning normally
     * after such a problem, but the existance of any uncaught exceptions
     * in your code is something you should really fix.
     */
    public void run() {
        try {
            boolean running = true;
            while (running) {
                try {
                    byte[] buffer = new byte[PircBot.BUFFER_SIZE];
                    int readBytes = -1;
                    String overflow = "";
                    while ((readBytes = _stream.read(buffer)) > -1) {
                        String encoding = _bot.detect(buffer);
                        if(logger.isDebugEnabled()) {
                            logger.debug("detected encoding: {}, will choose: {}", encoding, StringUtils.isBlank(encoding) ? _encoding : encoding);
                        }
                        if (StringUtils.isBlank(encoding)) {
                            encoding = _encoding;
                        }

                        String decodedBuffer = new String(buffer, 0, readBytes, Charset.forName(encoding));
                        String[] lines = (overflow + decodedBuffer).split("\\r?\\n");

                        // if the buffer does not end with a \n, then maybe the last sentence is not complete
                        // We need to save this part for the next round.
                        if(!decodedBuffer.endsWith("\n")) {
                            overflow = lines[lines.length-1];
                            lines = ArrayUtils.remove(lines, lines.length-1);
                        } else {
                            overflow = "";
                        }

                        for(String line: lines) {
                            if(StringUtils.isNotBlank(line)) {
                                try {
                                    _bot.handleLine(line);
                                } catch (Throwable t) {
                                    logger.error("Your implementation of PircBot is Faulty ! " +
                                            "PircBot will (possibly) continue operating, but you should check for the following error", t);
                                }
                            }
                        }
                    }
                    if (readBytes == -1) {
                        // The server must have disconnected us.
                        running = false;
                    }
                }
                catch (InterruptedIOException iioe) {
                    // This will happen if we haven't received anything from the server for a while.
                    // So we shall send it a ping to check that we are still connected.
                    this.sendRawLine("PING " + (System.currentTimeMillis() / 1000));
                    // Now we go back to listening for stuff from the server...
                }
            }
        }
        catch (Exception e) {
            // Do nothing.
        }
        
        // If we reach this point, then we must have disconnected.
        try {
            _socket.close();
        }
        catch (Exception e) {
            // Just assume the socket was already closed.
        }

        if (!_disposed) {
            logger.info("*** Disconnected.");
            _isConnected = false;
            _bot.onDisconnect();
        }
        
    }
    
    
    /**
     * Closes the socket without onDisconnect being called subsequently.
     */
    public void dispose () {
        try {
            _disposed = true;
            _socket.close();
        }
        catch (Exception e) {
            // Do nothing.
        }
    }
    
    private PircBot _bot = null;
    private Socket _socket = null;
    private InputStream _stream = null;
    private BufferedWriter _bwriter = null;
    private boolean _isConnected = true;
    private boolean _disposed = false;
    private String _encoding = null;
    
    public static final int MAX_LINE_LENGTH = 512;
    
}
