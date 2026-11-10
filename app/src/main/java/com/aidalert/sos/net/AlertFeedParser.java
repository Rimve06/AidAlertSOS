package com.aidalert.sos.net;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * XML parsing (SAX). Reads public alert feeds in three common shapes and returns plain Java objects:
 *   RSS 2.0     &lt;item&gt;  with title, description, pubDate
 *   Atom        &lt;entry&gt; with title, summary / content, updated
 *   CAP-like    &lt;info&gt;  with headline, description, effective
 * Pure Java (no Android classes) so it is unit tested on a PC.
 */
public final class AlertFeedParser {
    private AlertFeedParser() {}

    public static final class Item {
        public String title = "";
        public String description = "";
        public String date = "";
        public String link = "";
        public String area = "";
    }

    /** The SAX handler: a subclass of DefaultHandler that collects the fields of each record. */
    private static final class Handler extends DefaultHandler {
        final List<Item> items = new ArrayList<>();
        private Item current;
        private final StringBuilder text = new StringBuilder();

        private static String local(String qName) {
            int c = qName.indexOf(':');
            return (c >= 0 ? qName.substring(c + 1) : qName).toLowerCase();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            String n = local(qName);
            if (n.equals("item") || n.equals("entry") || n.equals("info")) current = new Item();
            // Atom: <link href="..."/>
            if (current != null && n.equals("link") && attrs != null && attrs.getValue("href") != null && current.link.isEmpty()) {
                current.link = attrs.getValue("href");
            }
            text.setLength(0);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            text.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String n = local(qName);
            if (current == null) return;
            String v = text.toString().trim();
            switch (n) {
                case "title":
                case "headline":
                    if (current.title.isEmpty()) current.title = v;
                    break;
                case "description":
                case "summary":
                case "content":
                    if (current.description.isEmpty()) current.description = v;
                    break;
                case "link":
                    if (current.link.isEmpty() && v.startsWith("http")) current.link = v;
                    break;
                case "country":
                case "areadesc":
                    if (current.area.isEmpty()) current.area = v;
                    break;
                case "pubdate":
                case "updated":
                case "effective":
                    if (current.date.isEmpty()) current.date = v;
                    break;
                case "item":
                case "entry":
                case "info":
                    if (!current.title.isEmpty()) items.add(current);
                    current = null;
                    break;
                default:
                    break;
            }
            text.setLength(0);
        }
    }

    public static List<Item> parse(String xml) throws SAXException, IOException, ParserConfigurationException {
        SAXParserFactory f = SAXParserFactory.newInstance();
        // Refuse DTDs / external entities (XXE). Parsers that do not know a feature simply ignore it.
        trySet(f, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySet(f, "http://xml.org/sax/features/external-general-entities", false);
        trySet(f, "http://xml.org/sax/features/external-parameter-entities", false);
        SAXParser p = f.newSAXParser();
        Handler h = new Handler();
        p.parse(new InputSource(new StringReader(xml)), h);
        return h.items;
    }

    private static void trySet(SAXParserFactory f, String feature, boolean value) {
        try {
            f.setFeature(feature, value);
        } catch (ParserConfigurationException | SAXException | RuntimeException ignored) {
            // feature not supported by this parser
        }
    }
}
