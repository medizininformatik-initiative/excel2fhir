package de.uni_leipzig.life.csv2fhir;

import java.io.File;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.uni_leipzig.life.csv2fhir.utils.CompressFileUtils;

/**
 * @author AXS (07.11.2021)
 */
public enum OutputFileType {
    JSON,
    JSONGZIP {
        @Override
        public OutputFileType getBaseFileType() {
            return JSON;
        }

        @Override
        public void compress(File file) throws Exception {
            CompressFileUtils.compressGzip(file);
        }
    },
    JSONBZ2 {
        @Override
        public OutputFileType getBaseFileType() {
            return JSON;
        }

        @Override
        public void compress(File file) throws Exception {
            CompressFileUtils.compressBZ2(file);
        }
    },
    NDJSON {
        @Override
        public boolean isMultiSinglePatientBundlesFileType() {
            return true;
        }
    },
    ZIPJSON {
        @Override
        public String getFileExtension() {
            return ".json.zip";
        }

        @Override
        public OutputFileType getBaseFileType() {
            return JSON;
        }

        @Override
        public boolean isMultiSinglePatientBundlesFileType() {
            return true;
        }
    },
    XML {
        @Override
        public IParser getParser() {
            return fhirContext.newXmlParser();
        }
    };

    public String getFileExtension() {
        return "." + toString().toLowerCase();
    }

    /** The context to generate the parser */
    private static final FhirContext fhirContext = FhirContext.forR4();

    /** */
    private IParser parser;

    /**
     * @return the parser to write the bundles
     */
    public IParser getParser() {
        if (parser == null) {
            parser = fhirContext.newJsonParser();
        }
        return parser;
    }

    /**
     * @return compressing file types return here the file type of the contained
     *         data
     */
    public OutputFileType getBaseFileType() {
        return null;
    }

    /**
     * @return <code>true</code> if the file type contains multiple entries of a
     *         bundle for a single patient.
     */
    public boolean isMultiSinglePatientBundlesFileType() {
        return false;
    }

    /**
     * @return
     */
    public boolean isCompressedFileType() {
        return getBaseFileType() != null;
    }

    /**
     * @return
     */
    public void compress(@SuppressWarnings("unused") File file) throws Exception {
        //default do nothing
    }

}
