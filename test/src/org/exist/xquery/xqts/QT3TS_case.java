/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.xqts;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import junit.framework.Assert;

import org.custommonkey.xmlunit.Diff;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.SAXSerializer;
import org.exist.w3c.tests.TestCase;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Variable;
import org.exist.xquery.VariableImpl;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class QT3TS_case extends TestCase {

	protected static final String FOLDER = "test/external/QT3-test-suite/";
	protected static final String QT_NS = "http://www.w3.org/2010/09/qt-fots-catalog";
    protected static final XmldbURI QT3_URI = XmldbURI.DB.append("QT3");
    
    protected static final String xquery3declaration = "xquery version \"3.0\";\n";

    @Override
    public void loadTS() throws Exception {
    	System.out.println("loading QT3...");
        QT3TS_To_junit convertor = new QT3TS_To_junit();
        convertor.init();
        try {
        	convertor.load();
        	System.out.println("loaded QT3.");
        } finally {
        	convertor.release();
        }
    }

    private Sequence enviroment(String file) throws Exception {
        DBBroker broker = null;
        XQuery xquery = null;

        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            xquery = broker.getXQueryService();

            broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);
            
            String query = "xmldb:document('"+file+"')";

            return xquery.execute(query, null, AccessContext.TEST);
            
        } finally {
        	pool.release(broker);
        }
    }
    
    private HashMap<String, Sequence> enviroments(String file) {
    	HashMap<String, Sequence> enviroments = new HashMap<String, Sequence>();

		DBBroker broker = null;
	    try {
	        broker = pool.get(pool.getSecurityManager().getSystemSubject());
	        XQuery xquery = broker.getXQueryService();
	
	        broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);
	        
	        String query = "declare namespace qt='"+QT_NS+"';\n"+
	        "let $testCases := xmldb:document('/db/QT3/"+file+"')\n"+
	        "let $tc := $testCases//qt:environment\n"+
	        "return $tc";

	        Sequence result = xquery.execute(query, null, AccessContext.TEST);
	        
	        String col = XmldbURI.create("/db/QT3/"+file).removeLastSegment().toString();
	
	        for (NodeProxy node : result.toNodeSet()) {
	        	ElementImpl el = (ElementImpl) node.getNode();

                String name = el.getAttribute("name");
                if (name == null)
                	continue;
                
                NodeList sources = el.getElementsByTagNameNS(QT_NS, "source");
                for (int j = 0; j < sources.getLength(); j++) {
                	ElementImpl source = (ElementImpl) sources.item(j);
                	
                	String role = source.getAttribute("role");
                    Assert.assertEquals(".", role);

                	String url = source.getAttribute("file");
                    Assert.assertFalse("".equals(url));
                    Assert.assertFalse(enviroments.containsKey(name));
                    try {
                    	enviroments.put(name, enviroment(col+"/"+url));
					} catch (Exception e) {
		                Assert.fail(e.getMessage());
					}
                }
            }
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			pool.release(broker);
		}
	    return enviroments;
    }
    
    private Sequence getEnviroment(String file, String name) {
    	Sequence enviroment = null;
    	
		DBBroker broker = null;
	    try {
	        broker = pool.get(pool.getSecurityManager().getSystemSubject());
	        XQuery xquery = broker.getXQueryService();
	
	        broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);
	        
	        String query = "declare namespace qt='"+QT_NS+"';\n"+
	        "let $testCases := xmldb:document('/db/QT3/"+file+"')\n"+
	        "let $tc := $testCases//qt:environment[@name eq '"+name+"']\n"+
	        "let $catalog := xmldb:document('/db/QT3/catalog.xml')\n"+
	        "let $cat := $catalog//qt:environment[@name eq '"+name+"']\n"+
	        "return ($tc, $cat)";

	        Sequence result = xquery.execute(query, null, AccessContext.TEST);
	        
	        String col = XmldbURI.create("/db/QT3/"+file).removeLastSegment().toString();
	
	        for (NodeProxy node : result.toNodeSet()) {
	        	ElementImpl el = (ElementImpl) node.getNode();

                String _name = el.getAttribute("name");
                if (_name == null)
                	continue;
                
                col = el.getDocument().getURI().removeLastSegment().toString();
                
                NodeList sources = el.getElementsByTagNameNS(QT_NS, "source");
                for (int j = 0; j < sources.getLength(); j++) {
                	ElementImpl source = (ElementImpl) sources.item(j);
                	
                	String role = source.getAttribute("role");
                    Assert.assertEquals(".", role);

                	String url = source.getAttribute("file");
                    Assert.assertFalse("".equals(url));
//                    Assert.assertFalse(enviroments.containsKey(name));
                    Assert.assertNull(enviroment);
                    try {
                    	enviroment = enviroment(col+"/"+url);
					} catch (Exception e) {
		                Assert.fail(e.getMessage());
					}
                }
            }
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			pool.release(broker);
		}
	    return enviroment;
    }


    protected void testCase(String file, String tcName) {
    	System.out.println("test "+tcName);
    	
        DBBroker broker = null;
        Sequence result = null;
        XQuery xquery = null;

    	try {
            XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");

            Set<String> extectedError = new HashSet<String>();
            try {
                broker = pool.get(pool.getSecurityManager().getSystemSubject());
                xquery = broker.getXQueryService();

                final XQueryContext context = new XQueryContext(pool, AccessContext.TEST);

                broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);

	            String query = "declare namespace qt='"+QT_NS+"';\n"+
	            "let $testCases := xmldb:document('/db/QT3/"+file+"')\n"+
	            "let $tc := $testCases//qt:test-case[@name eq \""+tcName+"\"]\n"+
	            "return $tc";
	
	            ResourceSet results = service.query(query);
	            
	            Assert.assertFalse("", results.getSize() != 1);
	
	            ElementImpl TC = (ElementImpl) ((XMLResource) results.getResource(0)).getContentAsDOM();
	
	            Sequence contextSequence = null;
	            
	            NodeList expected = null;
	            String nodeName = "";
	
	            //compile & evaluate
	            String caseScript = null;
	
	            NodeList childNodes = TC.getChildNodes();
	            for (int i = 0; i < childNodes.getLength(); i++) {
	                Node child = childNodes.item(i);
	                switch (child.getNodeType()) {
	                    case Node.ATTRIBUTE_NODE:
	//                        String name = ((Attr)child).getName();
	//                        if (name.equals("scenario"))
	//                            scenario = ((Attr)child).getValue();
	                        break;
	                    case Node.ELEMENT_NODE:
	                    	nodeName = ((ElementImpl) child).getLocalName();
	                        if (nodeName.equals("test")) {
	                            ElementImpl el = ((ElementImpl) child);
	                            caseScript = el.getNodeValue();
	                        
	                        } else if (nodeName.equals("environment")) {
	                            ElementImpl el = ((ElementImpl) child);
	                            
	                            String ref = el.getAttribute("ref");
	                            if (!(ref == null || "empty".equals(ref) || ref.isEmpty())) {
	                            	Assert.assertNull(contextSequence);
	                            	contextSequence = getEnviroment(file, ref);
	                            } else {
	                                NodeList _childNodes = el.getChildNodes();
	                                for (int j = 0; j < _childNodes.getLength(); j++) {
	                                    Node _child = _childNodes.item(j);
	                                    switch (_child.getNodeType()) {
	                                    case Node.ELEMENT_NODE:
	                                    	nodeName = ((ElementImpl) _child).getLocalName();
	                                        if (nodeName.equals("param")) {
	                                            el = ((ElementImpl) _child);
	                                        	Variable var = new VariableImpl(QName.parse(context, el.getAttribute("name")));
	                                        	
	                                        	String type = el.getAttribute("as");
	                                        	if ("xs:date".equals(type)) {
	                                            	var.setStaticType(Type.DATE);
	                                            	
	                                            	Sequence res = xquery.execute(el.getAttribute("select"), null, AccessContext.TEST);
	                                            	Assert.assertEquals(1, res.getItemCount());
	                                            	var.setValue(res);
	                                        	} else if ("xs:dateTime".equals(type)) {
	                                            	var.setStaticType(Type.DATE_TIME);
	                                            	
	                                            	Sequence res = xquery.execute(el.getAttribute("select"), null, AccessContext.TEST);
	                                            	Assert.assertEquals(1, res.getItemCount());
	                                            	var.setValue(res);
	                                        	} else {
	                                        		Assert.fail("unknown type '"+type+"'");
	                                        	}
	                                        	context.declareGlobalVariable(var);
	                                        }
	                                    }
	                                }
	                            }
	                            
	                        } else if (nodeName.equals("result")) {
	                            ElementImpl el = ((ElementImpl) child);
	                            
	                            possibleErrors(el, extectedError);
	
	                            NodeList anyOf = el.getElementsByTagNameNS(QT_NS, "any-of");
	                            for (int j = 0; j < anyOf.getLength(); j++) {
	                            	el = (ElementImpl) anyOf.item(j);
		                            possibleErrors(el, extectedError);
	                            }
	
	                            expected = el.getChildNodes();
	                        }
	                        break;
	                    default :
	                        ;
	                }
	            }
                
                final CompiledXQuery compiled = xquery.compile(context, xquery3declaration+caseScript);
                result = xquery.execute(compiled, contextSequence);

                for (int i = 0; i < expected.getLength(); i++) {
                	Node node = expected.item(i);
                	checkResults(node.getLocalName(), node.getChildNodes(), result);
                }
            } catch (XPathException e) {
            	ErrorCode errorCode = e.getErrorCode();
            	if (errorCode != null && extectedError.contains(errorCode.getErrorQName().getLocalName()))
            		return;
            	
                Assert.fail("expected error code '"+extectedError+"' get '"+e.getMessage()+"'");
            } catch (Exception e) {
            	e.printStackTrace();
                Assert.fail(e.getMessage());
            } finally {
            	pool.release(broker);
            }
        } catch (XMLDBException e) {
            Assert.fail(e.toString());
		} 
    }
    
    private void possibleErrors(ElementImpl el, Set<String> extectedError) {
        NodeList errors = el.getElementsByTagNameNS(QT_NS, "error");
        for (int j = 0; j < errors.getLength(); j++) {
        	ElementImpl error = (ElementImpl) errors.item(j);

            //check error for 'code' attribute
        	String code = error.getAttribute("code");
        	if (code != null && !code.isEmpty()) {
            	extectedError.add(code);
            }
        }
    }
    
    private void checkResults(String type, NodeList expected, Sequence result) throws Exception {
    		
		if ("all-of".equals(type)) {
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node node = expected.item(i);
	        	checkResults(node.getLocalName(), node.getChildNodes(), result);
	        }
		} else if ("any-of".equals(type)) {
			StringBuilder sb = new StringBuilder();
			sb.append("at any-of all failed\n");
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node node = expected.item(i);
	        	try {
	        		checkResults(node.getLocalName(), node.getChildNodes(), result);
	        		return;
	        	} catch (Throwable e) {
	        		e.printStackTrace();
	        		sb.append(e.getMessage()).append("\n");
				}
	        }
			Assert.assertTrue(sb.toString(), false);
			
		} else if ("assert".equals(type)) {
			Assert.assertTrue("not implemented 'assert'", false);

		} else if ("assert-type".equals(type)) {
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node node = expected.item(i);
	        	
	        	final String expect = node.getNodeValue();
	        	final int actual = result.itemAt(i).getType();
	        	
        		if (Type.subTypeOf(actual, Type.getType(expect)))
        			return;

	        	Assert.assertEquals("expected '"+expect+"' get '"+Type.getTypeName(actual),
        			Type.getType(expect), 
        			result.itemAt(i).getType()
    			); 
	        }

		} else if ("assert-eq".equals(type)) {
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node node = expected.item(i);
	        	String expect = node.getNodeValue();
	        	if ((expect.startsWith("\"") && expect.endsWith("\"")) || (expect.startsWith("'") && expect.endsWith("'")))
	        		//? check is it xs:string ?
		        	Assert.assertEquals(
	        			expect.substring(1, expect.length()-1), 
	        			result.itemAt(i).getStringValue()
	    			); 
	        	else
		        	Assert.assertEquals(
	        			expect, 
	        			result.itemAt(i).getStringValue()
	    			); 
	        }

		} else if ("assert-deep-eq".equals(type)) {
			Assert.assertTrue("not implemented 'assert-deep-eq'", false);
		
		} else if ("assert-true".equals(type)) {
			Assert.assertTrue("expecting true get false", result.effectiveBooleanValue());

		} else if ("assert-false".equals(type)) {
			Assert.assertFalse("expecting false get true", result.effectiveBooleanValue());

		} else if ("assert-string-value".equals(type)) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < result.getItemCount(); i++) {
				sb.append(result.itemAt(i).getStringValue());
				if (i+1 !=  result.getItemCount())
					sb.append(" ");
			}
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node node = expected.item(i);
	        	String expect = node.getNodeValue();

	        	Assert.assertEquals(
        			expect, 
        			sb.toString()
    			); 
	        }

		} else if ("assert-serialization-error".equals(type)) {
			Assert.assertTrue("not implemented 'assert-serialization-error'", false);

		} else if ("serialization-matches".equals(type)) {
			Assert.assertTrue("not implemented 'serialization-matches'", false);

		} else if ("assert-permutation".equals(type)) {
			Assert.assertTrue("not implemented 'assert-permutation'", false);

		} else if ("assert-count".equals(type)) {
			Assert.assertTrue("not implemented 'assert-count'", false);

		} else if ("assert-empty".equals(type)) {
			Assert.assertTrue("not implemented 'assert-empty'", false);

		} else if ("assert-xml".equals(type)) {
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node exNode = expected.item(i);
	        	String exString;
	        	if (exNode.getNodeType() == Node.ATTRIBUTE_NODE) {
	        		Attr attr = (Attr)exNode;
	        		if (attr.getName().equals("file")) {
                    	Sequence seq = enviroment(
                    			XmldbURI.create(attr.getBaseURI()).removeLastSegment()+"/"+attr.getValue()
                			);
                    	StringBuilder sb = new StringBuilder();
                    	for (int j = 0; j < seq.getItemCount(); j++) {
                    		sb.append(toString(seq.itemAt(j)));
                    	}
                    	exString = sb.toString();

	        		} else {
	        			Assert.assertTrue("unknown attribute '"+attr.getName()+"'", false);
	        			return;
	        		}
	        	} else {
	        		exString = exNode.getNodeValue();
	        	}
	        	final Item acNode = result.itemAt(i);
	        	Assert.assertTrue(
        			diffXML(exString, toString(acNode))
    			);
	        }

		} else if ("error".equals(type)) {
			Assert.assertTrue("not implemented 'error'", false);

		} else {
			Assert.assertTrue("unknown '"+type+"'", false);
		}
    }
    
	private static final Properties properties = new Properties();

	private String toString(Item item) throws SAXException {
        StringWriter writer = new StringWriter();
        SAXSerializer serializer = new SAXSerializer(writer, properties);
        item.toSAX(pool.getActiveBroker(), serializer, properties);
        String serialized = writer.toString();
        //System.out.println(serialized);
        return serialized;
    }
    

	private boolean diffXML(String expResult, String res) throws SAXException, IOException {
//		res = res.replaceAll("\n", "");
//		res = res.replaceAll("\t", "");
//		expResult = expResult.replaceAll("\n", "");
//		expResult = expResult.replaceAll("\t", "");
		
//		System.out.println(expResult);
//		System.out.println(res);

		Diff diff = new Diff(expResult.trim(), res);
        if (!diff.identical()) {
        	System.out.println("expected:");
        	System.out.println(expResult);
        	System.out.println("get:");
        	System.out.println(res);
        	System.out.println(diff.toString());
            return false;
        }
        
        return true;
	}
    
	private void diffXML(Node exNode, Node acNode) {
		Assert.assertTrue("expected: "+exNode+" actual:"+acNode, exNode.isEqualNode(acNode));
	}


    @Override
	protected String getCollection() {
		return QT3_URI.toString();
	}
}