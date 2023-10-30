package com.stimulsoft.forms;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Copyright Stimulsoft
 */
public class StiPdfFormSubmission {
    private byte OpenSBracketTag = "[".getBytes()[0];
    private byte CloseSBracketTag = "]".getBytes()[0];
    private byte CloseBracketTag = ")".getBytes()[0];
    private byte[] FieldsTag = "/Fields[".getBytes();
    private byte[] CloseFieldsTag = "]>>>>".getBytes();
    private byte[] BeginFieldTag = "<</T(".getBytes();
    private byte[] BeginValueTag = ")/V".getBytes();
    private byte[] CloseBracketSlTag = "\\)".getBytes();
    private byte[] OpenBracketSlTag = "\\(".getBytes();

    private HashMap<String, Object> data = new HashMap<String, Object>();

    public StiPdfFormSubmission() {
    }

    public StiPdfFormSubmission(HashMap<String, Object> data) {
        if (data != null) {
            this.data = data;
        }
    }

    public HashMap<String, Object> getData() {
        return data;
    }

    public void parseXFDF(byte[] xfdf) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException {
        data.clear();

        String strData = new String(xfdf, "UTF-8");

        if (strData != null && strData.length() > 0) {
            strData = strData.trim();
            if (strData.startsWith("<?xml")) {
                parseXML(xfdf);
            } else if (strData.startsWith("%FDF")) {
                parseFDF(xfdf);
            }
        }
    }

    private void parseFDF(byte[] xfdf) throws UnsupportedEncodingException {
        int pos = 0;
        while (pos < xfdf.length) {
            if (bytesEquals(FieldsTag, xfdf, pos)) {
                pos += FieldsTag.length;

                while (pos < xfdf.length) {
                    if (bytesEquals(BeginFieldTag, xfdf, pos)) {
                        List<Byte> fieldName = new ArrayList<Byte>();
                        List<List<Byte>> fieldValues = new ArrayList<List<Byte>>();
                        pos += BeginFieldTag.length - 1;

                        pos = parseValue(fieldName, xfdf, pos);

                        if (bytesEquals(BeginValueTag, xfdf, pos)) {
                            pos += 3;

                            if (xfdf[pos] == OpenSBracketTag) {
                                while (xfdf[pos + 1] != CloseSBracketTag) {
                                    pos++;
                                    List<Byte> fieldValue = new ArrayList<Byte>();
                                    pos = parseValue(fieldValue, xfdf, pos);
                                    fieldValues.add(fieldValue);
                                }
                            }

                            else {
                                List<Byte> fieldValue = new ArrayList<Byte>();
                                pos = parseValue(fieldValue, xfdf, pos) + 1;
                                fieldValues.add(fieldValue);
                            }

                            String fieldNameStr = encodeFDFValue(fieldName);
                            for (List<Byte> fieldValue : fieldValues) {
                                addValue(fieldNameStr, encodeFDFValue(fieldValue));
                            }
                        }

                        if (bytesEquals(CloseFieldsTag, xfdf, pos + 2))
                            return;
                    }
                    pos++;
                }
            }
            pos++;
        }
    }

    private String encodeFDFValue(List<Byte> bytesList) throws UnsupportedEncodingException {
        byte[] bytes = new byte[bytesList.size()];
        for (int i = 0; i < bytesList.size(); i++) {
            bytes[i] = bytesList.get(i).byteValue();
        }

        if (bytes.length > 1 && bytes[0] == (byte) 254 && bytes[1] == (byte) 255) {
            String encoded = new String(bytes, "UTF-16BE");
            return (encoded.length() > 0 && encoded.codePointAt(0) == 65279) ? encoded.substring(1) : encoded;
        } else
            return new String(bytes, "UTF-8");
    }

    private int parseValue(List<Byte> value, byte[] xfdf, int pos) {
        byte[] slTag = "\\\\".getBytes();
        pos++;
        while (!(CloseBracketTag == xfdf[pos] && !bytesEquals(CloseBracketSlTag, xfdf, pos - 1))) {
            if (bytesEquals(CloseBracketSlTag, xfdf, pos) || bytesEquals(OpenBracketSlTag, xfdf, pos) || bytesEquals(slTag, xfdf, pos)) {
                pos++;
            }
            value.add(xfdf[pos++]);
        }
        return pos;
    }

    private boolean bytesEquals(byte[] subBytes, byte[] bytes, int pos) {
        for (int i = 0; i < subBytes.length; i++)
            if (subBytes[i] != bytes[pos + i])
                return false;

        return true;
    }

    private void parseXML(byte[] xml) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml));
        XPath xPath = XPathFactory.newInstance().newXPath();

        NodeList nodeList = (NodeList) xPath.compile("/xfdf/fields/field").evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node fieldNode = nodeList.item(i);
            String fieldName = fieldNode.getAttributes().getNamedItem("name").getNodeValue();
            NodeList values = (NodeList) xPath.compile("value").evaluate(fieldNode, XPathConstants.NODESET);
            for (int j = 0; j < values.getLength(); j++) {
                Node value = values.item(j);
                addValue(fieldName, value.getTextContent());
            }

            NodeList fieldValue = (NodeList) xPath.compile("field").evaluate(fieldNode, XPathConstants.NODESET);
            for (int j = 0; j < fieldValue.getLength(); j++) {
                Node value = fieldValue.item(j);
                Node field2Name = value.getAttributes().getNamedItem("name");
                NodeList childNodes = value.getChildNodes();
                for (int k = 0; k < childNodes.getLength(); k++) {
                    Node child = childNodes.item(k);
                    if ("value".equals(child.getNodeName())) {
                        addValue(String.format("%s.%s", fieldName, field2Name.getTextContent()), child.getTextContent());
                    } else if ("field".equals(child.getNodeName()) && child.getChildNodes().getLength() > 0 && "value".equals(child.getChildNodes().item(0).getNodeName())) {
                        addValue(String.format("%s.%s.Text", fieldName, field2Name.getTextContent()), child.getTextContent());
                    }
                }
            }
        }
    }

    private void addValue(String name, String value) {
        if (!data.containsKey(name)) {
            data.put(name, value);
        } else {
            if (data.get(name) instanceof String) {
                List<String> list = new ArrayList<String>();
                list.add((String) data.get(name));
                data.put(name, list);
            }
            ((List<String>) data.get(name)).add(value);
        }
    }

    public String getStringValue(String name) {
        Object value = data.get(name);
        if (value instanceof List) {
            List listValue = (List) value;
            return listValue.size() > 0 ? listValue.get(0).toString() : null;
        } else if (value instanceof String) {
            return (String) value;
        } else if (value != null) {
            return value.toString();
        }

        return null;
    }

    public List<String> getListValue(String name) {
        Object value = data.get(name);
        if (value instanceof List) {
            return (List<String>) value;
        } else if (value != null) {
            List<String> result = new ArrayList<String>();
            result.add(value.toString());
            return result;
        }

        return null;
    }

    public Object getValue(String fieldName) {
        return data.get(fieldName);
    }

    public Double getDoubleValue(String fieldName) {
        try {
            return Double.parseDouble(getStringValue(fieldName));
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> getListBoxValue(String fieldName) {
        return getListValue(fieldName);
    }

    public String getComboBoxValue(String fieldName) {
        return getStringValue(fieldName);
    }

    public Temporal getDateTimeBoxValue(String fieldName, String pattern) {
        String strValue = getStringValue(fieldName);
        if (strValue == null) {
            return null;
        }

        if (strValue.startsWith("D:")) {
            int year = Integer.parseInt(strValue.substring(2, 6));
            int month = Integer.parseInt(strValue.substring(6, 8));
            int day = Integer.parseInt(strValue.substring(8, 10));
            int hour = strValue.length() > 10 ? Integer.parseInt(strValue.substring(10, 12)) : 0;
            int min = strValue.length() > 12 ? Integer.parseInt(strValue.substring(12, 14)) : 0;
            int sec = strValue.length() > 14 ? Integer.parseInt(strValue.substring(14, 16)) : 0;

            return LocalDateTime.of(year, month, day, hour, min, sec);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            try {
                return LocalDateTime.parse(strValue, formatter);
            } catch (Exception e) {
                return LocalDate.parse(strValue, formatter);
            }
        }
    }

    public LocalTime getTimeBoxValue(String fieldName) {
        return getTimeBoxValue(fieldName, "HH:mm:ss");
    }

    public LocalTime getTimeBoxValue(String fieldName, String pattern) {
        String strValue = getStringValue(fieldName);
        if (strValue == null) {
            return null;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalTime.parse(strValue, formatter);
    }

    public Boolean getCheckBoxValue(String fieldName) {
        String strValue = getStringValue(fieldName);
        return strValue != null ? !strValue.equalsIgnoreCase("off") && !strValue.equalsIgnoreCase("false") : null;
    }

    public List<String> getMultipleSelectionValue(String fieldName) {
        List<String> resultList = new ArrayList<String>();

        List<String> msKeys = new ArrayList<String>();
        for (String key : data.keySet()) {
            if (key.startsWith(fieldName + ".")) {
                msKeys.add(key);
            }
        }

        if (msKeys.size() == 0) {
            if (data.get(fieldName) != null) {
                resultList.addAll(getListValue(fieldName));
            }

            return resultList;
        }

        for (String key : msKeys) {
            String value = getStringValue(key);
            if (!key.contains("Custom") && value != null && value != "Off") {
                resultList.add(value);
            }
        }

        String customCheckBoxValue = getStringValue(fieldName + ".CheckBoxCustom");
        String customValue = getStringValue(fieldName + ".CheckBoxCustom.Text");
        if (customCheckBoxValue != null && customCheckBoxValue != "Off" && customValue != null && customValue.length() > 0) {
            resultList.add(customValue);
        }

        return resultList;
    }

    public String getSingleSelectionValue(String fieldName) {
        String value = getStringValue(fieldName);

        if (value == null || value.length() == 0 || value == "Off") {
            return "";
        } else if ("RadioButtonCustom".equals(value) && data.containsKey(fieldName + ".Text")) {
            return getStringValue(fieldName + ".Text");
        } else {
            return value;
        }
    }

    public String getTableFieldValue(String tableName, int columnIndex, int rowIndex) {
        String cellName = String.format("%s.Cell-%s-%s-", tableName, columnIndex, rowIndex);

        for (String key : data.keySet()) {
            if (key.startsWith(cellName)) {
                return getStringValue(key);
            }
        }

        // check single selection
        String rowName = String.format("%s.Row-%s-SingleSelection", tableName, rowIndex);
        String columnName = "Column-" + columnIndex;
        return data.get(rowName) != null && getStringValue(rowName).endsWith(columnName) ? "true" : null;
    }

    public String getTableTotalFieldValue(String tableName, int totalIndex) {
        String cellName = String.format("%s.TotalsField%s", tableName, totalIndex);

        for (String key : data.keySet()) {
            if (key.startsWith(cellName)) {
                return getStringValue(key);
            }
        }
        return null;
    }

    public boolean getTableFieldBoolValue(String tableName, int columnIndex, int rowIndex) {
        String strValue = getTableFieldValue(tableName, columnIndex, rowIndex);

        if (strValue == null)// try SingleSelection
        {
            String cellName = String.format("%s.Cell-%s-SingleSelection", tableName, columnIndex);
            if (data.get(cellName) != null) {
                strValue = getStringValue(cellName);
                return Integer.parseInt(strValue) == rowIndex;
            }
        }

        return strValue != null && (strValue != "Off");
    }

    public int getTableColumnsCount(String tableName) {
        int index = -1;

        for (String key : data.keySet()) {
            if (key.startsWith(tableName + ".Cell")) {
                String[] name = key.split("-");
                int col = Integer.parseInt(name[1]);
                index = Math.max(index, col + 1);
            }
        }

        return index;
    }

    public int getTableRowsCount(String tableName) {
        int index = -1;

        for (String key : data.keySet())
            if (key.startsWith(tableName + ".Cell")) {
                String[] name = key.split("-");
                int row = Integer.parseInt(name[2]);
                index = Math.max(index, row + 1);
            }

        return index;
    }

}
