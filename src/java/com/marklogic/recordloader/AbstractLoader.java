/**
 * Copyright (c) 2008 Mark Logic Corporation. All rights reserved.
 */
package com.marklogic.recordloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.CharsetDecoder;
import java.util.logging.Logger;

import com.marklogic.ps.SimpleLogger;
import com.marklogic.ps.Utilities;
import com.marklogic.ps.timing.TimedEvent;
import com.marklogic.xcc.DocumentFormat;

/**
 * @author Michael Blakeley, michael.blakeley@marklogic.com
 * 
 */
public abstract class AbstractLoader implements LoaderInterface {

    protected SimpleLogger logger;

    protected TimedEvent event;

    protected Configuration config;

    protected Monitor monitor;

    protected String currentRecordPath;

    protected String currentFileBasename;

    protected String currentUri;

    protected ContentInterface content;

    protected ContentFactory contentFactory;

    protected String startId;

    protected String entryPath;

    protected String fileBasename;

    protected CharsetDecoder decoder;

    protected InputStream input;

    protected File inputFile;

    protected String inputFilePath;

    protected DocumentFormat format = null;

    /**
     * @param _logger
     * 
     *            The abstract implementation does nothing. Subclasses may
     *            overload as needed. If problems are encountered, the subclass
     *            should throw a FatalException or another run-time exception.
     */
    public static void checkEnvironment(Logger _logger) {
        // do nothing
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.marklogic.recordloader.AbstractLoader#call()
     */
    public Object call() throws Exception {
        try {
            if (null != inputFile) {
                // time to instantiate the reader
                logger.fine("processing " + inputFilePath);
                setInput(new FileInputStream(inputFile), decoder);
            }
            event = new TimedEvent();
            process();
            return null;
        } catch (RuntimeException e) {
            // for NullPointerException, etc
            monitor.halt(e);
            return null;
        } catch (Exception e) {
            logger.warning("Exception "
                    + e.getMessage()
                    + " while processing "
                    + (null != currentUri ? currentUri
                            : currentRecordPath));
            throw e;
        } catch (Throwable t) {
            // for OutOfMemoryError, etc
            monitor.halt(t);
            return null;
        } finally {
            // clean up via monitor
            if (null != fileBasename && null != entryPath) {
                monitor.cleanup(fileBasename, entryPath);
            }
            if (null != input) {
                input.close();
                input = null;
            }
            if (null != inputFile) {
                inputFile = null;
            }
            if (null != contentFactory) {
                contentFactory.close();
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.marklogic.recordloader.LoaderInterface#process()
     * 
     * subclasses should override this method to extend it
     */
    @SuppressWarnings("unused")
    public void process() throws LoaderException {
        // safety check
        if (null == input) {
            throw new NullPointerException("caller must set input");
        }

        // cache some info locally
        startId = config.getStartId();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.marklogic.recordloader.LoaderInterface#setInput(java.io.InputStream)
     */
    @SuppressWarnings("unused")
    public void setInput(InputStream _is, CharsetDecoder _decoder)
            throws LoaderException {
        if (null == _is) {
            throw new NullPointerException("null input stream");
        }
        if (null == _decoder) {
            throw new NullPointerException("null charset decoder");
        }
        input = _is;
        decoder = _decoder;
    }

    /**
     * 
     */
    protected void cleanupInput() {
        cleanupRecord();
        if (null != input) {
            try {
                input.close();
            } catch (IOException e) {
                // nothing we can do about it
                logger.logException(e);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.marklogic.recordloader.LoaderInterface#setInput(java.io.File)
     */
    public void setInput(File _file, CharsetDecoder _decoder)
            throws LoaderException {
        if (null == _file) {
            throw new NullPointerException("null input file");
        }
        if (null == _decoder) {
            throw new NullPointerException("null charset decoder");
        }
        // defer opening it until call()
        inputFile = _file;
        try {
            inputFilePath = inputFile.getCanonicalPath();
        } catch (IOException e) {
            throw new LoaderException(e);
        }
        decoder = _decoder;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.marklogic.recordloader.LoaderInterface#setFileBasename(java.lang.
     * String)
     */
    public void setFileBasename(String _name) throws LoaderException {
        fileBasename = _name;
        if (null == _name) {
            return;
        }
        currentFileBasename = Utilities.stripExtension(_name);
        logger.fine("using fileBasename = " + _name);

        // don't tell the contentFactory unless config says it's ok
        if (config.isUseFilenameCollection()) {
            contentFactory.setFileBasename(_name);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.marklogic.recordloader.LoaderInterface#setRecordPath(java.lang.String
     * )
     */
    public void setRecordPath(String _path) throws LoaderException {
        entryPath = _path;
        // replace and coalesce any backslashes with slash
        if (config.isInputNormalizePaths()) {
            currentRecordPath = _path.replaceAll("[\\\\]+", "/");
        } else {
            currentRecordPath = _path;
        }

        // this form of URI() does escaping nicely
        if (config.isUseFilenameIds()) {
            try {
                currentRecordPath = new URI(null, currentRecordPath, null)
                        .toString();
            } catch (URISyntaxException e) {
                throw new LoaderException(e);
            }
        }

    }

    /**
     * @param len
     * 
     */
    protected void updateMonitor(long len) {
        // handle monitor accounting
        // note that we count skipped records, too
        event.increment(len);
        monitor.add(currentUri, event);
    }

    /**
     * @throws LoaderException
     */
    protected void insert() throws LoaderException {
        logger.fine("inserting " + currentUri);
        content.insert();
    }

    /**
     * 
     */
    protected void cleanupRecord() {
        // clean up
        logger.fine("content = " + content);
        if (null != content) {
            content.close();
        }
        content = null;
        currentUri = null;
    }

    private boolean checkStartId(String id) {
        if (null == startId) {
            return false;
        }

        // we're still scanning for the startid:
        // is this my cow?
        if (!startId.equals(id)) {
            // don't bother to open the stream: skip this record
            monitor.incrementSkipped("id " + id + " != " + startId);
            return true;
        }

        logger.info("found START_ID " + id);
        startId = null;
        config.setStartId(null);
        monitor.resetThreadPool();
        return false;
    }

    /**
     * @param _id
     * @return
     * @throws IOException
     * @throws LoaderException
     */
    protected boolean checkIdAndUri(String _id) throws LoaderException,
            IOException {
        return checkStartId(_id) || checkExistingUri(currentUri);
    }

    protected String composeUri(String id) throws IOException {
        logger.finest(id);
        if (null == id) {
            throw new IOException("id may not be null");
        }

        String cleanId = id.trim();

        // TODO move this to the end?
        String inputStripPrefix = config.getInputStripPrefix();
        if (null != inputStripPrefix && inputStripPrefix.length() > 0) {
            cleanId = cleanId.replaceFirst(inputStripPrefix, "");
        }

        if (cleanId.length() < 1) {
            throw new IOException("id may not be empty");
        }

        // automatically use the current file, if available
        // note that config.getUriPrefix() will ensure that the uri ends in '/'
        // TODO differentiate between files and zip archives?
        StringBuilder baseName = new StringBuilder(config.getUriPrefix());
        baseName.append((currentFileBasename == null
                || currentFileBasename.equals("") || config
                .isUseFilenameIds()) ? "" : currentFileBasename);
        if (baseName != null && baseName.length() > 0
                && '/' != baseName.charAt(baseName.length() - 1)) {
            baseName.append("/");
        }
        baseName.append(cleanId);
        baseName.append(config.getUriSuffix());

        String finalName = baseName.toString();
        logger.finest(finalName);
        return finalName;
    }

    /**
     * @param uri
     * @return
     * @throws IOException
     * @throws LoaderException
     */
    private boolean checkExistingUri(String uri) throws LoaderException,
            IOException {
        // return true if we're supposed to check,
        // and if the document already exists
        if (config.isSkipExisting() || config.isErrorExisting()) {
            boolean exists = content.checkDocumentUri(uri);
            logger.fine("checking for uri " + uri + " = " + exists);
            if (exists) {
                if (config.isErrorExisting()) {
                    throw new IOException(
                            "ERROR_EXISTING=true, cannot overwrite existing document: "
                                    + uri);
                }
                // ok, must be skipExisting...
                // count it and log the message
                monitor.incrementSkipped("existing uri " + uri);
                return true;
            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.marklogic.recordloader.LoaderInterface#setConfiguration(com.marklogic
     * .recordloader.Configuration)
     */
    @SuppressWarnings("unused")
    public void setConfiguration(Configuration _config)
            throws LoaderException {
        config = _config;
        logger = config.getLogger();
        if (null == format) {
            format = config.getFormat();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.marklogic.recordloader.LoaderInterface#setConnectionUri(java.net.URI)
     */
    public void setConnectionUri(URI _uri) throws LoaderException {
        if (null == config) {
            throw new NullPointerException(
                    "must call setConfiguration() before setUri()");
        }

        // load the correct content factory
        try {
            contentFactory = config.getContentFactoryConstructor()
                    .newInstance(new Object[] {});
        } catch (Exception e) {
            logger.logException(e);
            throw new FatalException(e);
        }
        contentFactory.setConfiguration(config);
        contentFactory.setConnectionUri(_uri);
    }

    /*
     * (non-Javadoc)
     * 
     * @seecom.marklogic.recordloader.LoaderInterface#setMonitor(com.marklogic.
     * recordloader.Monitor)
     */
    @SuppressWarnings("unused")
    public void setMonitor(Monitor _monitor) throws LoaderException {
        monitor = _monitor;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.marklogic.recordloader.LoaderInterface#setFormat(com.marklogic.xcc
     * .DocumentFormat)
     */
    public void setFormat(DocumentFormat _xml) {
        format = _xml;
    }

}