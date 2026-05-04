/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.linuxforhealth.fhir.cql.translator.impl;

import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.CqlCompilerOptions;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.quick.FhirLibrarySourceProvider;
import org.cqframework.cql.elm.serializing.ElmLibraryWriterFactory;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.opencds.cqf.cql.engine.execution.CqlLibraryReader;

import org.linuxforhealth.fhir.cql.translator.CqlTranslationException;

/**
 * Provide an implementation of the CqlTranslationProvider interface that uses
 * the CQL translator directly inside the JVM. 
 */
public class InJVMCqlTranslationProvider extends BaseCqlTranslationProvider {

    private static final Logger LOG = Logger.getLogger(InJVMCqlTranslationProvider.class.getName());
    
    private ModelManager modelManager;
    private final List<LibrarySourceProvider> registeredProviders = new ArrayList<>();
    
    public InJVMCqlTranslationProvider() {
        this.modelManager = new ModelManager();
        addLibrarySourceProvider(new FhirLibrarySourceProvider());
    }

    public InJVMCqlTranslationProvider(LibraryManager libraryManager, ModelManager modelManager) {
        this.modelManager = modelManager;
        // Copy registered providers from libraryManager is not possible via interface;
        // callers should use addLibrarySourceProvider after construction.
    }

    public InJVMCqlTranslationProvider(LibrarySourceProvider provider) {
        this();
        addLibrarySourceProvider(provider);
    }

    public InJVMCqlTranslationProvider addLibrarySourceProvider(LibrarySourceProvider provider) {
        registeredProviders.add(provider);
        return this;
    }

    @Override
    public List<Library> translate(InputStream cql, List<Option> options, Format targetFormat) throws CqlTranslationException {
        List<Library> result = new ArrayList<>();

        CqlCompilerOptions compilerOptions;
        if (options != null) {
            CqlCompilerOptions.Options[] optionArr = options.stream()
                    .map(o -> CqlCompilerOptions.Options.valueOf(o.name()))
                    .toArray(CqlCompilerOptions.Options[]::new);
            compilerOptions = new CqlCompilerOptions(CqlCompilerException.ErrorSeverity.Info,
                    LibraryBuilder.SignatureLevel.None, optionArr);
        } else {
            compilerOptions = new CqlCompilerOptions(CqlCompilerException.ErrorSeverity.Info,
                    LibraryBuilder.SignatureLevel.None);
        }

        LibraryManager localLibraryManager = new LibraryManager(modelManager, compilerOptions);
        for (LibrarySourceProvider provider : registeredProviders) {
            localLibraryManager.getLibrarySourceLoader().registerProvider(provider);
        }

        try {
            CqlTranslator translator = CqlTranslator.fromStream(cql, localLibraryManager);
    
            LOG.info(String.format("Translated CQL contains %d errors, %d exceptions",
                    translator.getErrors().size(), translator.getExceptions().size()));
            
            List<CqlCompilerException> badStuff = new ArrayList<>();
            // the translator will duplicate exceptions with assigned severity in the errors, warnings, and messages lists
            badStuff.addAll(translator.getExceptions().stream().filter(e -> e.getSeverity() == null).collect(Collectors.toList()));
            badStuff.addAll(translator.getErrors());
            if (!badStuff.isEmpty()) {                
                throw new CqlTranslationException(formatMsg(badStuff));
            }
            
            if (translator.getWarnings().size() > 0) {
                LOG.warning(String.format("Translated CQL contains warnings: %s", formatMsg(translator.getWarnings())));
            }
    
            switch (targetFormat) {
            case XML:
                Library fromStream = CqlLibraryReader.read(new StringReader(translator.toXml()));
                result.add(fromStream);
                
                // Anything else that was translated via includes and the LibrarySourceProvider
                // interface will get returned as well
                org.cqframework.cql.elm.serializing.ElmLibraryWriter xmlWriter =
                        ElmLibraryWriterFactory.getWriter("application/elm+xml");
                for (Map.Entry<org.hl7.elm.r1.VersionedIdentifier, org.hl7.elm.r1.Library> entry : translator.getLibraries().entrySet()) {
                    String libraryXml = xmlWriter.writeAsString(entry.getValue());
                    Library dependency = CqlLibraryReader.read(new StringReader(libraryXml));
                    result.add(dependency);
                }
                break;
            default:
                throw new CqlTranslationException(String.format("The CQL Engine does not support format %s", targetFormat.name()));
            }
        } catch (CqlTranslationException cqex) {
            throw cqex;
        } catch (Exception ex) {
            throw new CqlTranslationException("CQL translation failed", ex);
        }

        return result;
    }
    
    /**
     * This was cribbed from the CQL Translation Server TranslationFailureException.
     * 
     * @param translationErrs List of translation errors.
     * @return String representation of the list of translation errors.
     */
    private static String formatMsg(List<CqlCompilerException> translationErrs) {
        StringBuilder msg = new StringBuilder();
        msg.append("Translation failed due to errors:");
        for (CqlCompilerException error : translationErrs) {
          TrackBack tb = error.getLocator();
          String lines = tb == null ? "[n/a]" : String.format("[%s:%s %d:%d-%d:%d]",
                  tb.getLibrary().getId(), tb.getLibrary().getVersion(),
                  tb.getStartLine(), tb.getStartChar(), tb.getEndLine(),
                  tb.getEndChar());
          msg.append(String.format("%s %s%n", lines, error.getMessage()));
        }
        return msg.toString();
    }
}
