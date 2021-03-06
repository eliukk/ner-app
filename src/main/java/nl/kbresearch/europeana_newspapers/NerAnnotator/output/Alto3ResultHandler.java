package nl.kbresearch.europeana_newspapers.NerAnnotator.output; 

import nl.kbresearch.europeana_newspapers.NerAnnotator.TextElementsExtractor; 
import nl.kbresearch.europeana_newspapers.NerAnnotator.container.ContainerContext; 

import java.io.*; 

import java.text.SimpleDateFormat; 
import java.util.ArrayList; 
import java.util.Calendar; 
import java.util.HashMap; 
import java.util.List; 

import javax.xml.transform.*; 
import javax.xml.transform.dom.*; 
import javax.xml.transform.stream.*; 

import org.w3c.dom.Comment; 
import org.w3c.dom.Document; 
import org.w3c.dom.Element; 
import org.w3c.dom.NodeList; 

/**
 * @author Willem Jan Faber
 *
 */

public class Alto3ResultHandler implements ResultHandler {

    private ContainerContext context; 
    private String name; 
    private PrintWriter outputFile; 
    private Document altoDocument; 
    private String versionString; 
    private List<HashMap> Entity_list = new ArrayList(); 

    String continuationId = null; 
    String continuationLabel = null; 

    String prevWord = ""; 
    String prevType = ""; 
    String prevWordid = ""; 
    String alreadyAddedTagId = ""; 
    ArrayList <String> prevWordIds = new ArrayList <String>(); 

    boolean prevIsNamed = false; 

    int tagCounter = 0; 

    /**
     * @param context
     * @param name
     */
    public Alto3ResultHandler(final ContainerContext context, final String name, final String versionString) {
        this.context = context; 
        this.name = name; 
        this.versionString = versionString; 
    }

    @Override
    public void startDocument() {
    }

    @Override
    public void startTextBlock() {
    }

    @Override
    public void newLine(boolean hyphenated) {
    }

    @Override
    public void addToken(String wordid, String originalContent, String word, String label, String continuationid) {
        HashMap mMap = new HashMap(); 

        // try to find out if this is a continuation of the previous word
        if (continuationid != null) {
            this.continuationId = continuationid; 
            this.continuationLabel = label; 
        }

        if (wordid.equals(this.continuationId)) {
            label = this.continuationLabel; 
        }

        if (label != null) {
            // Reformat the label to a more readable form.
            if ((label.equals("B-LOC")) || (label.equals("I-LOC"))) {
                label = "LOC"; 
            }
            if ((label.equals("B-PER")) || (label.equals("I-PER"))) {
                label = "PER"; 
            }
            if ((label.equals("B-ORG") || (label.equals("I-ORG")))) {
                label = "ORG"; 
            }
            if ((label.equals("B-MISC") || (label.equals("I-MISC")))) {
                label = "MISC"; 
            }

            // Find the alto node with the corresponding wordid.
            // Needed for addint the TAGREFS attribute to the ALTO_string.
            Element domElement = TextElementsExtractor.findAltoElementByStringID(altoDocument, wordid); 

            if ((this.prevIsNamed) && (this.prevType.equals(label))) {
                // This is a continuation of a label, eg. J.A de Vries..
                // prevIsNamed indicates that the previous word was also a NE
                // Concatenation string to generate one label.

                if ( ! word.equalsIgnoreCase(this.prevWord.trim())) {
                    // Don't double label names on a hypened word.
                    word = this.prevWord + word;
                }

                prevWordIds.add(this.prevWordid);

                this.prevWord = word + " ";
                this.prevType = label;
                this.prevWordid = wordid;

                this.Entity_list.remove(this.Entity_list.size()-1);

                //If there is not tag with same label and word already added to Entity_list (namedentitytag list), then it is
                //going to be added to the list. 
                if (!tagAlreadyExists(label, word, domElement)){

                    // Add the TAGREFS attribute to the corresponding String in the alto.
                    if (this.tagCounter > 0) {
                        // Prevent negative tag numbers :)
                        domElement.setAttribute("TAGREFS", "Tag" + String.valueOf(this.tagCounter - 1));
                        mMap.put("id", String.valueOf(this.tagCounter - 1));
                    } else {
                        domElement.setAttribute("TAGREFS", "Tag" + String.valueOf(this.tagCounter));
                        mMap.put("id", String.valueOf(this.tagCounter));
                    }

                    // Create mapping for the TAGS header part of alto3
                    mMap.put("label", label);
                    mMap.put("word", word);

                    // Add tagref mapping to the list.
                    this.Entity_list.add(mMap);
                }
                else{
                    //Updates earlier words tagrefs to match current alreadyAddedTagid, because this was a continuation of a label
                    //and there was already a tag for these worlds. Also sets tagCounter to correct number.

                    this.tagCounter -=1;
                    updateTAGREFS(prevWordIds);
                }
            } else {

                    prevWordIds.clear();

                    //If there is not tag with same label and word already added to Entity_list (namedentitytag list), then it is
                    //going to be added to the list. 
                    if (!tagAlreadyExists(label, word, domElement)){
                        // Add the TAGREFS attribute to the corresponding String in the alto.
                        domElement.setAttribute("TAGREFS", "Tag" + String.valueOf(this.tagCounter));

                        // Create mapping for the TAGS header part of alto3
                        mMap.put("id", String.valueOf(this.tagCounter));
                        mMap.put("label", label);
                        mMap.put("word", word);

                        // Add tagref mapping to the list.
                        this.Entity_list.add(mMap);
                        this.tagCounter += 1;
                    }
                    this.prevWord = word + " ";
                    this.prevType = label;
                    this.prevWordid = wordid;
                }
            this.prevIsNamed = true;

        
        } else {
            this.prevIsNamed = false;
        }
    }

    //Goes through Entity_list (namedentitytag list). This sets alreadyAddedTagId to domElement's TAGREFS attribute value,
    // if there is already tag defined, that has same label and word defined.
    private boolean tagAlreadyExists(String label, String word, Element domElement) {

        for (int i = 0; i < this.Entity_list.size(); i++) {

            HashMap entity = this.Entity_list.get(i); 

            String labelOld = (String)entity.get("label"); 
            String wordOld = (String)entity.get("word"); 


            if (labelOld.equals(label) && wordOld.equals(word)) {

                this.alreadyAddedTagId = (String)entity.get("id"); 
                domElement.setAttribute("TAGREFS", "Tag" + alreadyAddedTagId); 

                return true; 
            }
        }

        return false; 
    }

    private void updateTAGREFS(ArrayList <String> wordids) {

        for (int i = 0; i < wordids.size(); i++) {
            Element domElement = TextElementsExtractor.findAltoElementByStringID(this.altoDocument, wordids.get(i)); 
            domElement.setAttribute("TAGREFS", "Tag" + this.alreadyAddedTagId); 
        }
    }

    @Override
    public void stopTextBlock() {
    }

    @Override
    public void stopDocument() {
        // Create xml:
        // <Tags><NamedEntityTag ID="Tag7" LABEL="Person" DESCRIPTION="James M Bigstaff "/></Tags>
        // Entity_list is populated with known NE's
        Element child = altoDocument.createElement("Tags");
        for (HashMap s: this.Entity_list) {
            Element childOfTheChild = altoDocument.createElement("NamedEntityTag");
            childOfTheChild.setAttribute("LABEL", (String) s.get("label"));
            childOfTheChild.setAttribute("DESCRIPTION", (String) s.get("word"));
            childOfTheChild.setAttribute("ID", "Tag" + (String) s.get("id"));
            child.appendChild(childOfTheChild);
        }

        altoDocument.getDocumentElement().appendChild(child);

        NodeList alto = altoDocument.getElementsByTagName("alto");
        Element alto_root = (Element) alto.item(0);
        alto_root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        alto_root.setAttribute("xmlns" , "http://www.loc.gov/standards/alto/ns-v3#");
        alto_root.setAttribute("xsi:schemaLocation", "http://www.loc.gov/standards/alto/ns-v3# https://www.loc.gov/standards/alto/v3/alto.xsd");

        try {
            // Output file for alto3 format.
            outputFile = new PrintWriter(new File(context.getOutputDirectory(), name + ".alto3.xml"), "UTF-8");

            Element element = altoDocument.getDocumentElement();
            // Get current date, and add it to the comment line
            Calendar currentDate = Calendar.getInstance();
            SimpleDateFormat formatter= new SimpleDateFormat("yyyy/MMM/dd HH:mm:ss");
            String dateNow = formatter.format(currentDate.getTime());
            versionString += " Date/time NER-extraction: " + dateNow + "\n";

            // Add the version information to the output xml.
            Comment comment = altoDocument.createComment(versionString);
            element.getParentNode().insertBefore(comment, element);

            DOMSource domSource = new DOMSource(altoDocument);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();

            // Reformat output, because of additional nodes added.
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            // Set the right output encoding
            transformer.setOutputProperty("encoding", "UTF-8");
            // Transform the input document to output
            transformer.transform(domSource, result);

            // Store results to file.
            outputFile.print(writer.toString());
            outputFile.flush();
            outputFile.close();

        } catch(TransformerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void globalShutdown() {
    }

    @Override
    public void setAltoDocument(Document doc) {
        altoDocument = doc;
    }
}
