package nl.kbresearch.europeana_newspapers.NerAnnotator.alto;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import nl.kbresearch.europeana_newspapers.NerAnnotator.NERClassifiers;
import nl.kbresearch.europeana_newspapers.NerAnnotator.TextElementsExtractor;
import nl.kbresearch.europeana_newspapers.NerAnnotator.output.ResultHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * ALTO file processing
 * 
 * @author rene
 * 
 */
public class AltoProcessor {


    private static String cleanWord(String attr) {
        String cleaned = attr.replace(".", "");
        cleaned = cleaned.replace(",", "");
        return cleaned;
    }


    /**
     * @param potentialAltoFilename
     * @param mimeType
     * @param lang
     * @param handler
     * @throws IOException
     */
    public static void handlePotentialAltoFile(final URL potentialAltoFilename,
            final String mimeType, final Locale lang, final ResultHandler[] handler)
            throws IOException {
        if ("text/xml".equalsIgnoreCase(mimeType)
                || potentialAltoFilename.getFile().endsWith(".xml")) {
            try {
                System.out.println("Trying to process ALTO file "
                        + potentialAltoFilename);
                long startTime = System.currentTimeMillis();
                Document doc = Jsoup.parse(potentialAltoFilename.openStream(),
                        "UTF-8", "", Parser.xmlParser());
                doc.outputSettings().escapeMode(EscapeMode.xhtml);
                Elements elementsByTag = doc.getElementsByTag("alto");
                if (elementsByTag.isEmpty()) {
                    System.err.println("Does not seem to be a ALTO file: "
                            + potentialAltoFilename.toExternalForm());
                    return;
                }

                // we have an Alto file
                for (ResultHandler h : handler) {
                    h.startDocument();
                    h.setAltoDocument(doc);
                }

                @SuppressWarnings("unchecked")
                CRFClassifier<CoreMap> classifier_alto = (CRFClassifier<CoreMap>) NERClassifiers.getCRFClassifierForLanguage(lang);
                CRFClassifier classifier_text = NERClassifiers.getCRFClassifierForLanguage(lang);
                List<List<CoreMap>> coreMapElements = TextElementsExtractor.getCoreMapElements(doc);

                int totalNumberOfWords = 0;
                int classified = 0;

                Map<String,String> awnser = new HashMap<String, String>();

                for (List<CoreMap> block : coreMapElements) {
                    int sentenceCount = 0;
                    int offset = 0;
                    int offsetCount = 0;

                    for (ResultHandler h : handler) {
                        h.startTextBlock();
                    }
                    
                    // Loop over the alto to extract text elements. Make one long string (sentance).
                    List<CoreMap> classify_alto = classifier_alto.classify(block);
                    String text = "";

                    for (CoreMap label : classify_alto) {
                        if (label.get(HyphenatedLineBreak.class) == null) {
                            String word = cleanWord(label.get(OriginalContent.class));
                            text = text + word + " ";
                            // label:
                            // [OriginalContent=Verhulst; TextAnnotation=Verhulst; AltoStringID=69:3233:45:880:29:677:3237:37:143 AnswerAnnotation=O]
                            // [OriginalContent=Rhap TextAnnotation=Rhapsodie AltoStringID=69:3233:45:880:33:850:3242:35:79 ContinuationAltoStringID=69:3274:43:878:1:69:3275:30:70 AnswerAnnotation=O]
                        }
                    }

                    ArrayList<Map<String , String>> stanford_tokens  = new ArrayList<Map<String,String>>();
                    // Classify the output text, using the stanford tokenizer.
                    List<List<CoreLabel>> out = classifier_text.classify(text);
                    Map<String, String> map = new HashMap<String, String>();

                    // Loop over the stanford tokenzied words to map them to the alto later on.
                    for (List<CoreLabel> sentence : out) {
                        for (CoreLabel label: sentence) {
                            if (label.get(HyphenatedLineBreak.class) == null) {
                                StringTokenizer st = new StringTokenizer(label.get(OriginalTextAnnotation.class));
                                // Sometimes the stanford tokenizer does not cut on whitespace (with numbers).
                                while (st.hasMoreTokens()) {
                                    awnser = new HashMap<String, String>();
                                    awnser.put(st.nextToken(), label.get(AnswerAnnotation.class));
                                    stanford_tokens.add(offsetCount, awnser);
                                    offsetCount += 1;
                                }
                                // label :
                                // [ValueAnnotation=Verhulst TextAnnotation=Verhulst OriginalTextAnnotation=Verhulst CharacterOffsetBeginAnnotation=260 
                                // CharacterOffsetEndAnnotation=268 BeforeAnnotation=  PositionAnnotation=60 ShapeAnnotation=Xxxxx GoldAnswerAnnotation=null AnswerAnnotation=B-LOC]
                                // [ValueAnnotation=; TextAnnotation=; OriginalTextAnnotation=; CharacterOffsetBeginAnnotation=268 CharacterOffsetEndAnnotation=269 BeforeAnnotation= 
                                // PositionAnnotation=61 ShapeAnnotation=; GoldAnswerAnnotation=null AnswerAnnotation=O]
                                // [ValueAnnotation=Rhap TextAnnotation=Rhap OriginalTextAnnotation=Rhap CharacterOffsetBeginAnnotation=270 CharacterOffsetEndAnnotation=274 BeforeAnnotation=  
                                // PositionAnnotation=62 ShapeAnnotation=Xxxx GoldAnswerAnnotation=null AnswerAnnotation=O]
                            }
                        }
                    }

                    for (CoreMap label : classify_alto) {
                        if (label.get(HyphenatedLineBreak.class) != null) {
                            for (ResultHandler h : handler) {
                                h.newLine(label.get(HyphenatedLineBreak.class));
                            }
                        } else {
                            // label :
                            // [OriginalContent=Verhulst; TextAnnotation=Verhulst; AltoStringID=69:3233:45:880:29:677:3237:37:143 AnswerAnnotation=O]
                            // [OriginalContent=Rhap TextAnnotation=Rhapsodie AltoStringID=69:3233:45:880:33:850:3242:35:79 ContinuationAltoStringID=69:3274:43:878:1:69:3275:30:70 AnswerAnnotation=O]
                            boolean match = false;
                            String stanfordClassification = "O";
                            String stanford = "";

                            // Matching the stanford tokenized output to the alto format.
                            if (label.get(TextAnnotation.class) != null) {
                                if (sentenceCount + offset < stanford_tokens.size()) {
                                    Set<String> stanfordKeyset = stanford_tokens.get(sentenceCount + offset).keySet();
                                    stanford = cleanWord(stanfordKeyset.toArray(new String[stanfordKeyset.size()])[0]);
                                    stanfordClassification = (stanford_tokens.get(sentenceCount + offset).get(stanford));
                                    if (label.get(TextAnnotation.class).equals("")) {
                                        stanford = "";
                                        offset -= 1;
                                    }
                                    while (!match) {
                                        // System.out.println("Want: " + cleanWord(label.get(TextAnnotation.class)));
                                        // System.out.println("Got: " + cleanWord(stanford));
                                        if (stanford.equals(cleanWord(label.get(TextAnnotation.class)))) {
                                            match = true;
                                            label.set(AnswerAnnotation.class, stanfordClassification);
                                        } else {
                                            offset += 1;
                                            if (sentenceCount + offset < stanford_tokens.size()) {
                                                stanfordKeyset = stanford_tokens.get(sentenceCount + offset).keySet();
                                                stanford = stanford + stanfordKeyset.toArray(new String[stanfordKeyset.size()])[0];
                                            } else {
                                                match = true;
                                            }
                                        }
                                    }
                                }
                            } else {
                                offset -= 1;
                            }
                            // System.out.println(stanford_tokens);
                            // System.out.println(label.get(OriginalContent.class));
                            // System.out.println(stanford);
                            if (!label.get(AnswerAnnotation.class).equals("O")) {
                                classified += 1;
                                // System.out.println(text);
                                // System.out.println(label.get(TextAnnotation.class));
                                for (ResultHandler h : handler) {
                                    h.addToken(
                                            label.get(AltoStringID.class),
                                            label.get(OriginalContent.class),
                                            label.get(TextAnnotation.class),
                                            label.get(AnswerAnnotation.class),
                                            label.get(ContinuationAltoStringID.class));
                                }
                            } else {
                                for (ResultHandler h : handler) {
                                    h.addToken(
                                            label.get(AltoStringID.class),
                                            label.get(OriginalContent.class),
                                            label.get(TextAnnotation.class),
                                            null,
                                            label.get(ContinuationAltoStringID.class));
                                }
                            }
                            totalNumberOfWords += 1;
                            sentenceCount += 1;
                        }
                    }
                    for (ResultHandler h : handler) {
                        h.stopTextBlock();
                    }

                }

                for (ResultHandler h : handler) {
                    h.stopDocument();
                }

                System.out.println();
                System.out.println("Statistics: "
                        + classified
                        + " out of "
                        + totalNumberOfWords
                        + "/ a " 
                        + ((double) classified / (double) totalNumberOfWords) + ") classified");
                System.out.println("Total millisecs: "
                        + (System.currentTimeMillis() - startTime));

            } catch (IOException e) {
                System.err.println("Could not read ALTO file "
                        + potentialAltoFilename.toExternalForm());
                throw e;
            } finally {
                for (ResultHandler h : handler) {
                    h.close();
                }
            }
        }
    }
}
