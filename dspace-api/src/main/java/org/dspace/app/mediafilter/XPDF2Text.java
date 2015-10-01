/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.mediafilter;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Utils;

/**
 * Text MediaFilter for PDF sources
 *
 * This filter produces extracted text suitable for building an index,
 * but not for display to end users.
 * It forks a process running the "pdftotext" program from the
 * XPdf suite -- see http://www.foolabs.com/xpdf/
 * This is a suite of open-source PDF tools that has been widely ported
 * to Unix platforms and the ones we use (pdftoppm, pdftotext) even
 * run on Win32.
 *
 * This was written for the FACADE project but it is not directly connected
 * to any of the other FACADE-specific software.  The FACADE UI expects
 * to find thumbnail images for 3D PDFs generated by this filter.
 *
 * Requires DSpace config properties keys:
 *
 *  xpdf.path.pdftotext -- path to "pdftotext" executable (required!)
 *
 * @author Larry Stone
 * @see org.dspace.app.mediafilter.MediaFilter
 */
public class XPDF2Text extends MediaFilter
{
    private static Logger log = Logger.getLogger(XPDF2Text.class);

    // Command to get text from pdf; @infile@, @COMMAND@ are placeholders
    protected static final String XPDF_PDFTOTEXT_COMMAND[] =
    {
        "@COMMAND@", "-q", "-enc", "UTF-8", "@infile@", "-"
    };


    // executable path that comes from DSpace config at runtime.
    private String pdftotextPath = null;

    @Override
    public String getFilteredName(String oldFilename)
    {
        return oldFilename + ".txt";
    }

    @Override
    public String getBundleName()
    {
        return "TEXT";
    }

    @Override
    public String getFormatString()
    {
        return "Text";
    }

    @Override
    public String getDescription()
    {
        return "Extracted Text";
    }

    @Override
    public InputStream getDestinationStream(Item currentItem, InputStream sourceStream, boolean verbose)
            throws Exception
    {
        // get configured value for path to XPDF command:
        if (pdftotextPath == null)
        {
            pdftotextPath = ConfigurationManager.getProperty("xpdf.path.pdftotext");
            if (pdftotextPath == null)
            {
                throw new IllegalStateException("No value for key \"xpdf.path.pdftotext\" in DSpace configuration!  Should be path to XPDF pdftotext executable.");
            }
        }

        File sourceTmp = File.createTempFile("DSfilt",".pdf");
        sourceTmp.deleteOnExit();  // extra insurance, we'll delete it here.
        int status = -1;
        try
        {
            // make local temp copy of source PDF since PDF tools
            // require a file for random access.
            // XXX fixme could optimize if we ever get an interface to grab asset *files*
            OutputStream sto = new FileOutputStream(sourceTmp);
            Utils.copy(sourceStream, sto);
            sto.close();
            sourceStream.close();

            String pdfCmd[] = XPDF_PDFTOTEXT_COMMAND.clone();
            pdfCmd[0] = pdftotextPath;
            pdfCmd[4] = sourceTmp.toString();

            log.debug("Running command: "+Arrays.deepToString(pdfCmd));
            Process pdfProc = Runtime.getRuntime().exec(pdfCmd);
            InputStream stdout = pdfProc.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Utils.copy(new BufferedInputStream(stdout), baos);
            stdout.close();
            baos.close();

            status = pdfProc.waitFor();
            String msg = null;
            if (status == 1)
            {
                msg = "pdftotext failed opening input: file=" + sourceTmp.toString();
            }
            else if (status == 3)
            {
                msg = "pdftotext permission failure (perhaps copying of text from this document is not allowed - check PDF file's internal permissions): file=" + sourceTmp.toString();
            }
            else if (status != 0)
            {
                msg = "pdftotext failed, maybe corrupt PDF? status=" + String.valueOf(status);
            }

            if (msg != null)
            {
                log.error(msg);
                throw new IOException(msg);
            }

            return new ByteArrayInputStream(baos.toByteArray());
        }
        catch (InterruptedException e)
        {
            log.error("Failed in pdftotext subprocess: ",e);
            throw e;
        }
        finally
        {
            if (!sourceTmp.delete())
            {
                log.error("Unable to delete temporary file");
            }
            if (status != 0)
            {
                log.error("PDF conversion proc failed, returns=" + status + ", file=" + sourceTmp);
            }
        }
    }
}

 	  	 
