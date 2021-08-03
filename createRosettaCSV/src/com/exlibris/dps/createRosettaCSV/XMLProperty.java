package com.exlibris.dps.createRosettaCSV;

import java.util.HashMap;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLProperty {

	static private HashMap<String, HashMap<String, String>> sections;
	
	public XMLProperty()
	{
		sections = new HashMap<String, HashMap<String, String>>();
	}
	
	/*
	 * Read XML file, create hashmap for each section
	 */
	public void load(String filename) throws Exception
	{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		org.w3c.dom.Document doc = db.parse(filename);
 
		Element root = doc.getDocumentElement();
		NodeList nodes = root.getElementsByTagName("section");
		int number_sections = nodes.getLength();

		for (int i = 0; i < number_sections; i++) 
		{
			Element section = (Element) nodes.item(i);
			parseSection(section);
		}
	}
	
	private void parseSection(Element xsection) throws Exception
	{
		String name =  xsection.getAttribute("name");
		if (name.isEmpty())
			throw new Exception("Section name missed");
		
		HashMap<String, String> section = new HashMap<String, String>();
		NodeList children = xsection.getChildNodes();
		if (children == null)
			return;
		fillSection(children, section);
		sections.put(name, section);
	}
		

	private void fillSection(NodeList nl, HashMap<String,String> m)
	{
		int num = nl.getLength();
		 
		for (int i = 0; i < num; i++)
		{
			Node n = nl.item(i);
			if(n.getNodeType() == Node.ELEMENT_NODE)
			{
				Element e = (Element) n;
			if (e.getNodeType() == Node.ELEMENT_NODE)
				m.put(e.getNodeName(), e.getFirstChild().getNodeValue());
			}
		}
	}
		
	/*
	 * Return property value
	 */
	public String getProperty(String section, String name)
	{
		HashMap<String, String> s = sections.get(section);
		if (s != null)
			return (s.get(name));
		return(null);
	}

	/*
	 * Return set of tag names
	 */
	public Set<String> getNames(String section)
	{
		HashMap<String, String> s = sections.get(section);
		if (s != null)
			return(s.keySet());
		return(null);
	}
	
	/**
	 * Test XML Style Properties
	 */
	public static void main(String[] args) {

		XMLProperty myself = new XMLProperty();
		try {
		myself.load("args[0]");
			
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}

}
