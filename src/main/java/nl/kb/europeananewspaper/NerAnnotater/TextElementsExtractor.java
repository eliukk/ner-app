package nl.kb.europeananewspaper.NerAnnotater;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import nl.kb.europeananewspaper.NerAnnotater.alto.AltoStringID;
import nl.kb.europeananewspaper.NerAnnotater.alto.ContinuationAltoStringID;
import nl.kb.europeananewspaper.NerAnnotater.alto.HyphenatedLineBreak;
import nl.kb.europeananewspaper.NerAnnotater.alto.OriginalContent;

import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/*
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

import org.xml.sax.*;
import org.w3c.dom.*;
*/

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

/**
 * Converter from ALTO elements to tokens for Stanford NER
 * 
 * @author rene
 * 
 */
public class TextElementsExtractor {

	private static final Logger logger = Logger
			.getLogger("TextElementsExtractor.class");

	/**
	 * @param altoDocument
	 * @return a list of text blocks, represented by their tokens.
	 */
	public static List<List<CoreMap>> getCoreMapElements(Document altoDocument) {
		List<List<CoreMap>> result = new LinkedList<List<CoreMap>>();

		Elements blocks = altoDocument.getElementsByTag("TextBlock");

                //NodeList blocks = dom.getElementsByTagName("TextBlock");
                //for (int i = 0; i<blocks.getLength(); i++) {
                //Node tokens = blocks.item(i);
                //if (tokens.getNodeType() == Node.ELEMENT_NODE) {
		for (Element e : blocks) {
			List<CoreMap> newBlock = new LinkedList<CoreMap>();

                        //Element eElement = (Element) tokens;
                        //NodeList textLineToken = eElement.getElementsByTagName("TextLine");
			Elements tokens = e.getElementsByTag("TextLine");
			Boolean firstSegmentAfterHyphenation = false;
                        //for (int j = 0; j<textLineToken.getLength(); j++) {
			for (Element token : tokens) {
                                //Node tl = textLineToken.item(j);
                                //if (tl.getNodeType() == Node.ELEMENT_NODE) {
                                //Element tll = (Element) tl;
                                //NodeList text = tll.getChildNodes();
				Elements textLineTokens = token.children();
                                //for (int k =0;k<text.getLength(); k++) {
                                //if (text.item(k).getNodeType() == Node.ELEMENT_NODE) {
                                //Element tx = (Element) text.item(k);
				boolean hyphenatedEnd = false;
				for (Element textLineToken : textLineTokens) {
                                        //if (tx.getTagName() == "String") {
					if (textLineToken.tagName().equalsIgnoreCase("string")) {
						newBlock.add(getWordToLabel(textLineToken,
								firstSegmentAfterHyphenation));
						firstSegmentAfterHyphenation = false;
                                        //if (tx.getTagName() == "Hyp") {
					} else if (textLineToken.tagName().equalsIgnoreCase("hyp")) {
						hyphenatedEnd = true;
						firstSegmentAfterHyphenation = true;
					}
				}
				newBlock.add(getLineBreak(hyphenatedEnd));
			}

			result.add(newBlock);
		}
		return result;
	}

	private static CoreMap getLineBreak(boolean hyphenatedEnd) {
		CoreMap lineBreak = new CoreLabel();
		lineBreak.set(HyphenatedLineBreak.class, hyphenatedEnd);
		return lineBreak;
	}

	private static CoreLabel getWordToLabel(Element token,
			Boolean wordSegmentAfterHyphenation) {

                // Needs fixing..

		boolean continuesNextLine = false;
		String cleanedContent;
		Element nextNextSibling = null;

		if (wordSegmentAfterHyphenation) {
			cleanedContent = null;
		} else {
			// if the word is at the end of line and hyphenated, pull the
			// content from the second for NER into the first token
			String nextWordSuffix = "";
			Element nextSibling = token.nextElementSibling();

			if (nextSibling != null
					&& nextSibling.tagName().equalsIgnoreCase("hyp")) {
				// get first String element of next line, if it exists
				Element nextLine = nextSibling.parent().nextElementSibling();
				if (nextLine != null) {
					nextNextSibling = nextLine.child(0);
					if (nextNextSibling != null) {
						nextWordSuffix = nextNextSibling.attr("CONTENT");
						if (nextWordSuffix == null)
							nextWordSuffix = "";
						else {
							continuesNextLine = true;
						}
					}
				}
			}

			cleanedContent = cleanWord(token.attr("CONTENT") + nextWordSuffix);
		}

		CoreLabel label = new CoreLabel();
		label.set(OriginalContent.class, token.attr("CONTENT"));
		label.setWord(cleanedContent);
		label.set(AltoStringID.class, calcuateAltoStringID(token));
		if (continuesNextLine) {
			label.set(ContinuationAltoStringID.class,
					calcuateAltoStringID(nextNextSibling));
		}
		return label;
	}

	private static String cleanWord(String attr) {
		String cleaned = attr.replace(".", "");
		cleaned = cleaned.replace(",", "");
		return cleaned;
	}

	private static String calcuateAltoStringID(Element word) {
		Element parent = word.parent();
                //Element parent = word.getParentNode();

		String parentHpos = nullsafe(parent.attr("HPOS"));
		String parentVpos = nullsafe(parent.attr("VPOS"));
		String parentWidth = nullsafe(parent.attr("WIDTH"));
		String parentHeight = nullsafe(parent.attr("HEIGHT"));
		String optionalStringHpos = nullsafe(word.attr("HPOS"));
		String optionalStringVpos = nullsafe(word.attr("VPOS"));
		String optionalStringWidth = nullsafe(word.attr("WIDTH"));
		String optionalStringHeight = nullsafe(word.attr("HEIGHT"));

                //String parentHpos = nullsafe(parent.getAttribute("HPOS"));
		//String parentVpos = nullsafe(parent.getAttribute("VPOS"));
		//String parentWidth = nullsafe(parent.getAttribute("WIDTH"));
		//String parentHeight = nullsafe(parent.getAttribute("HEIGHT"));
		//String optionalStringHpos = nullsafe(word.getAttribute("HPOS"));
		//String optionalStringVpos = nullsafe(word.getAttribute("VPOS"));
		//String optionalStringWidth = nullsafe(word.getAttribute("WIDTH"));
		//String optionalStringHeight = nullsafe(word.getAttribute("HEIGHT"));

		LinkedList<String> params = new LinkedList<String>();
		params.add(parentHpos);
		params.add(parentVpos);
		params.add(parentHeight);
		params.add(parentWidth);
		params.add(Integer.toString(word.siblingIndex()));
		params.add(optionalStringHpos);
		params.add(optionalStringVpos);
		params.add(optionalStringHeight);
		params.add(optionalStringWidth);

		return StringUtil.join(params, ":");
	}

	public static Element findAltoElementByStringID(Document altoDocument,
			String id) {

		if (id == null || id.isEmpty()) {
			logger.warning("Trying to find element in ALTO document , with empty or null id");
			return null;
		}

		if (altoDocument == null) {
			logger.warning("Trying to find an element in an ALTO document, which is null");
			return null;
		}

		String[] split = id.split(":");
		if (split.length != 9) {
			logger.warning("id does not seem to have the right format. Has "
					+ split.length + " instead of 9 parameters");
			return null;
		}


                //String expression = "//String[@HPOS=" + split[0] + "][@VPOS='" + split[1] + "'][@HEIGHT='" + split[2] + "'][@WIDTH='" + split[3] + "']"; 
                //XPath xpath = XPathFactory.newInstance().newXPath();
                //try {
                //NodeList nodes = (NodeList) xpath.evaluate(expression, altoDocument, XPathConstants.NODESET);
                //Element ep = (Element) nodes.item(0);
                //return(ep);
                //} catch (XPathExpressionException ee) { }
                //

		Elements textlines = altoDocument.select("[HPOS=" + split[0] + "]")
				.select("[VPOS=" + split[1] + "]")
				.select("[HEIGHT=" + split[2] + "]")
				.select("[WIDTH=" + split[3] + "]");

		for (Element elem:textlines) {

                    // Unsure what this does acctually..
		    if (new Integer(split[4]) <= elem.childNodeSize()) {
	    		Element word = (Element) elem.childNode(new Integer(split[4]));
		    	if (word!=null) {
			    	return word;
			    }
            }
		}
		return null;

	}

	private static String nullsafe(String attr) {
		return attr == null ? "" : attr;
	}
}
