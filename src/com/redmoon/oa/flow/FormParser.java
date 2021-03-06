package com.redmoon.oa.flow;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.ResKeyException;
import cn.js.fan.web.SkinUtil;
import com.cloudwebsoft.framework.util.NetUtil;
import com.redmoon.oa.kernel.License;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.util.RequestUtil;
import com.redmoon.oa.visual.ModuleSetupDb;
import com.redmoon.weixin.util.HttpPostFileUtil;
import org.apache.commons.lang3.StringEscapeUtils;
import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.filters.AndFilter;
import org.htmlparser.filters.HasAttributeFilter;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.nodes.TextNode;
import org.htmlparser.tags.InputTag;
import org.htmlparser.tags.OptionTag;
import org.htmlparser.tags.SelectTag;
import org.htmlparser.tags.TextareaTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import SuperDog.DogStatus;
import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.StrUtil;
import cn.js.fan.web.Global;

import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.Config;
import com.redmoon.oa.flow.macroctl.MacroCtlMgr;
import com.redmoon.oa.flow.macroctl.MacroCtlUnit;
import com.redmoon.oa.flow.macroctl.NestSheetCtl;
import com.redmoon.oa.flow.macroctl.NestTableCtl;
import com.redmoon.oa.superCheck.CheckSuperKey;
import com.redmoon.oa.visual.FuncUtil;

/**
 * <p>
 * Title:
 * </p>
 * 
 * <p>
 * Description:
 * </p>
 * 
 * <p>
 * Copyright: Copyright (c) 2005
 * </p>
 * 
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */
public class FormParser {
	public final String DEFAULTVALUE = "default";

	public FormParser() {
		checkSuper();
	}

	String content;

	Vector fields = new Vector();

	public FormParser(String content) throws ResKeyException {
		checkSuper();
		this.content = content;

		com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
		String cloudUrl = cfg.get("cloudUrl");
		cloudUrl += "/public/module/parseForm.do";

/*		HashMap<String, String[]> reqMap = new HashMap<String, String[]>();
		reqMap.put("content", new String[]{content});

		License lic = License.getInstance();
		reqMap.put("licNum", new String[]{lic.getEnterpriseNum()});
		reqMap.put("licName", new String[]{lic.getName()});
		reqMap.put("licCompany", new String[]{lic.getCompany()});
		reqMap.put("licType", new String[]{lic.getType()});
		reqMap.put("licKind", new String[]{lic.getKind()});
		reqMap.put("licDomain", new String[]{lic.getDomain()});
		reqMap.put("licVersion", new String[]{lic.getVersion()});
		// reqMap.put("cwsIp", new String[]{request.getServerName()});
		reqMap.put("cwsVersion", new String[]{cfg.get("version")});

		String retStr = NetUtil.post(cloudUrl, reqMap);
		if ("".equals(retStr)) {
			// throw new ErrMsgException(SkinUtil.LoadString(request, "err_network"));
			// DebugUtil.log(getClass(), "FormParser", "网络连接错误！");
			throw new ResKeyException("err_network");
		}*/

		String retStr = "";
		File file = new File(Global.getRealPath() + "/WEB-INF/license.dat");
		HttpPostFileUtil post = null;
		try {
			post = new HttpPostFileUtil(cloudUrl);
			post.addParameter("license", file);
			post.addParameter("content", content);
			retStr = post.send();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch(FileNotFoundException e) {
			e.printStackTrace();
			throw new ResKeyException("err_lic_not_found");
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (retStr==null || "".equals(retStr)) {
			// DebugUtil.log(getClass(), "FormParser", "网络连接错误！");
			throw new ResKeyException("err_network");
		}

		boolean re = false;
		try {
			JSONObject json = new JSONObject(retStr);
			int ret = json.getInt("ret");
			if (ret==1) {
				JSONArray jsonAry = json.getJSONArray("fields");
				for (int i=0; i<jsonAry.length(); i++) {
					JSONObject js = jsonAry.getJSONObject(i);

					FormField ff = new FormField();
					ff.setName(js.getString("name"));
					ff.setTitle(js.getString("title"));
					ff.setType(js.getString("type"));
					ff.setPresent(js.getString("present"));
					if (!js.isNull("value")) {
						ff.setValue(js.getString("value"));
					}
					else {
						ff.setValue("");
					}
					ff.setFieldType(js.getInt("fieldType"));
					ff.setReadonly(js.getBoolean("readOnly"));
					ff.setDescription(js.getString("description"));
					ff.setRule(js.getString("rule"));
					ff.setDefaultValue(js.getString("defaultValue"));
					ff.setCssWidth(js.getString("cssWidth"));
					ff.setFunc(js.getBoolean("func"));
					ff.setCanNull(js.getBoolean("canNull"));
					ff.setMacroType(js.getString("macroType"));
					fields.addElement(ff);
				}
			}
			else {
				String msg = json.getString("msg");
				DebugUtil.log(getClass(), "FormParser", msg);
				throw new ResKeyException(msg);
			}
		} catch (JSONException e) {
			DebugUtil.log(getClass(), "FormParser", "retStr=" + retStr);
			e.printStackTrace();
		}
	}

	public Vector getFields() {
		return fields;
	}

	public boolean isKeywords(String fieldName) {
		return SQLGeneratorFactory.getSQLGenerator().isFieldKeywords(fieldName);
	}
	/**
     * 校验超级狗
     */
    private void checkSuper(){
    	//校验超级狗
    	CheckSuperKey csdk = CheckSuperKey.getInstance();
    	Config cfg = new Config();
    	try {
	    	int status = csdk.checkKey();
			//验证失败
			if (status != DogStatus.DOG_STATUS_OK){
				cfg.put("systemIsOpen", "false");
				cfg.put("systemStatus", "请使用正版授权系统");
			}
    	}catch (Exception e) {
			// TODO Auto-generated catch block
			cfg.put("systemIsOpen", "false");
			cfg.put("systemStatus", "请使用正版授权系统");
		}
    }
	/**
	 * 检查fiels中域的合法性及是否重复
	 * 
	 * @return boolean
	 */
	public boolean validateFields() throws ErrMsgException {
		int len = fields.size();
		for (int i = 0; i < len; i++) {
			FormField ff = (FormField) fields.get(i);
			// 根据控件类型，置数据类型，以免紊乱
			ff.checkFieldTypeAccordCtlType();

/*			if (ff.getName().equals("xm")) {
				throw new ErrMsgException(ff.getTitle() + " 的字段不能使用关键字xm！");
			}*/

			if (isKeywords(ff.getName()))
				throw new ErrMsgException(ff.getTitle() + " 的编码不能使用SQL的关键字："
						+ ff.getName());
			if (!StrUtil.isNotCN(ff.getName()))
				throw new ErrMsgException(ff.getTitle() + " 的编码："
						+ ff.getName() + " 不能使用中文名称！");
			if (StrUtil.isNumeric(ff.getName()))
				throw new ErrMsgException(ff.getTitle() + " 的编码："
						+ ff.getName() + " 不能使用数字！");
			for (int j = i + 1; j < len; j++) {
				FormField ff2 = (FormField) fields.get(j);
				if (!ff2.getType().equals(FormField.TYPE_RADIO)) {
					if (ff.getName().equals(ff2.getName())) {
						throw new ErrMsgException(ff.getTitle() + " 与 "
								+ ff2.getTitle() + " 编码重复，都为：" + ff.getName()
								+ "！");
					}
				}
			}
		}
		return true;
	}

	/**
	 * 获取HTML控件的属性
	 * 
	 * @param attName
	 *            String 属性名称
	 * @param htmlCtlString
	 *            String HTML控件字符串中的部分或全部）
	 * @return String 属性值，如果没有该属性，则返回为null
	 */
	public static String getAttribute(String attName, String htmlCtlString) {
		String pStr = attName + "=\"(.*?)\"";
		Pattern pa = Pattern.compile(pStr, Pattern.DOTALL
				| Pattern.CASE_INSENSITIVE);
		Matcher ma = pa.matcher(htmlCtlString);
		if (ma.find()) {
			if (ma.groupCount() == 1) {
				return StrUtil.getNullStr(ma.group(1));
			}
		}
		return null;
	}

	/**
	 * 替换input型的控件，用于报表模式显示，2013-1-15 fgf
	 * 
	 * @param content
	 *            String
	 */
	public static String replaceTextfieldWithValue(HttpServletRequest request,
			String content, FormDAO fdao, FormDb fd) {
		RequestUtil.setFormDAO(request, fdao);

        Parser parser;
        try {
            MacroCtlMgr mcm = new MacroCtlMgr();
            Pattern SCRIPT_TAG_PATTERN = Pattern.compile(
                    "<script[^>]*>(.*?)</script[^>]*>", Pattern.DOTALL
                            | Pattern.CASE_INSENSITIVE);
            // "<script>(.*?)</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            boolean isFound = true;
            do {
                parser = new Parser(content);
                parser.setEncoding("utf-8");//
                TagNameFilter filter = new TagNameFilter("input");
                NodeList nodes = parser.parse(filter);
                if (nodes == null || nodes.size() == 0) {
                    isFound = false;
                }
                else {
                    InputTag node = (InputTag)nodes.elementAt(0);
                    String value = StringEscapeUtils.unescapeHtml3(node.getAttribute("value"));

                    FormField ff = fdao.getFormField(node.getAttribute("name"));
                    String realValue = StrUtil.getNullStr(fdao.getFieldValue(ff.getName()));

                    if (ff.getType().equals(FormField.TYPE_MACRO)) {
                        String macroType = ff.getMacroType();
                        if (macroType.equals("nest_table")) {
                            // 加入嵌套表格
                            NestTableCtl nac = new NestTableCtl();
                            request.setAttribute("cwsId", "" + fdao.getId());
                            request.setAttribute("pageType", "archive");
                            String str = nac.getNestTable(request, ff);
                            // 去除script，以免其中jquery的$带来异常java.lang.IllegalArgumentException:
                            // Illegal group reference
                            Matcher matScript = SCRIPT_TAG_PATTERN.matcher(str);
                            str = matScript.replaceAll("");
                            realValue = str;
                        } else if (macroType.equals("nest_sheet")
                                || macroType.equals("macro_detaillist_ctl")) {
                            // 加入嵌套表格2
                            NestSheetCtl nac = new NestSheetCtl();
                            request.setAttribute("cwsId", "" + fdao.getId());
                            request.setAttribute("pageType", "archive");
                            request.setAttribute("formCode", fd.getCode());
                            String str = nac.getNestSheet(request, ff);
                            // 去除script，以免其中jquery的$带来异常java.lang.IllegalArgumentException:
                            // Illegal group reference
                            Matcher matScript = SCRIPT_TAG_PATTERN.matcher(str);
                            str = matScript.replaceAll("");
                            realValue = str;
                        } else {
                            MacroCtlUnit mcu = mcm.getMacroCtlUnit(macroType);
                            realValue = mcu.getIFormMacroCtl().converToHtml(
                                    request, ff, realValue);
                            realValue = StrUtil.getNullStr(realValue);
                        }
                    } else {
                        if (ff.getType().equals(FormField.TYPE_RADIO)) {
                            // radio控件中，当为
                            if (!"".equals(realValue) && value.equals(realValue)) {
                                realValue = "<img src='" + Global.getRootPath(request) + "/images/radio_y.gif' />";
                            } else {
                                realValue = "<img src='" + Global.getRootPath(request) + "/images/radio_n.gif' />";
                            }
                        } else if (ff.getType().equals(FormField.TYPE_CHECKBOX)) {
                            if (!"".equals(realValue) && value.equals(realValue)) {
                                realValue = "<img src='" + Global.getRootPath(request) + "/images/checkbox_y.gif' />";
                            } else {
                                realValue = "<img src='" + Global.getRootPath(request) + "/images/checkbox_n.gif' />";
                            }
                        }
                    }

                    int s = node.getStartPosition();
                    int e = node.getEndPosition();
                    String c = content.substring(0, s);
                    c += realValue;
                    c += content.substring(e);
                    content = c;
                }
            } while (isFound);
        } catch (ParserException e) {
            e.printStackTrace();
        }
        return content;
	}

	/**
	 * 替换Select型的控件，用于报表模式显示，2013-1-15 fgf
	 * 
	 * @param request
	 *            HttpServletRequest
	 * @param content
	 *            String
	 * @param fdao
	 *            FormDAO
	 * @param fd
	 *            FormDb
	 * @return String
	 */
	public static String replaceSelectWithValue(HttpServletRequest request,
			String content, FormDAO fdao, FormDb fd) {
        Parser parser;
		try {
			boolean isFound = true;
			do {
				parser = new Parser(content);
				parser.setEncoding("utf-8");//
				TagNameFilter filter = new TagNameFilter("select");  
				NodeList nodes = parser.parse(filter);
				if (nodes == null || nodes.size() == 0) {
					isFound = false;
				}		
				else {
					SelectTag node = (SelectTag)nodes.elementAt(0);
					String realValue = StrUtil.getNullStr(fdao.getFieldValue(node.getAttribute("name")));
					int s = node.getStartPosition();
					int e = node.getEndPosition();
					String c = content.substring(0, s);
					c += realValue;
					c += content.substring(e);
					content = c;
				}
			} while (isFound);
		} catch (ParserException e) {
			e.printStackTrace();
		}		
		
		return content;  		
	}

	/**
	 * 替换textarea型的控件，用于报表模式显示，2013-1-15 fgf
	 * 
	 * @param request
	 *            HttpServletRequest
	 * @param content
	 *            String
	 * @param fdao
	 *            FormDAO
	 * @param fd
	 *            FormDb
	 * @return String
	 */
	public static String replaceTextAreaWithValue(HttpServletRequest request,
			String content, FormDAO fdao, FormDb fd) {
		Parser parser;
		try {
			boolean isFound = true;
			do {
				parser = new Parser(content);
				parser.setEncoding("utf-8");
				TagNameFilter filter = new TagNameFilter("textarea");
				NodeList nodes = parser.parse(filter);
				if (nodes == null || nodes.size() == 0) {
					isFound = false;
				}
				else {
					TextareaTag node = (TextareaTag)nodes.elementAt(0);
					String realValue = StrUtil.getNullStr(fdao.getFieldValue(node.getAttribute("name")));
					int s = node.getStartPosition();
					int e = node.getEndPosition();
					String c = content.substring(0, s);
					c += realValue;
					c += content.substring(e);
					content = c;
				}
			} while (isFound);
		} catch (ParserException e) {
			e.printStackTrace();
		}
		return content;
	}

	public static String[][] getOptionsArrayOfRadio(FormDb fd, FormField ff) {
		String[][] ary = new String[0][0];
		String content = fd.getContent();
		Parser parser;
		try {
			parser = new Parser(content);
			parser.setEncoding("utf-8");//
			AndFilter filter = new AndFilter(new TagNameFilter("span"),
					new HasAttributeFilter("orgname", ff.getName()));
			NodeList nodes = parser.parse(filter);//
			
			if (nodes == null || nodes.size() == 0) {
				return ary;
			}

			Node node = nodes.elementAt(0);
			// System.out.println("aaaa===" + node.toHtml());
			NodeList nodesChildren = node.getChildren();
			int size = nodesChildren.size();
			ary = new String[size/2][2];
			for (int i=0; i<=size-2; i+=2) {
				Node nd = nodesChildren.elementAt(i);
				String value = "", text = "";
				if (nd instanceof InputTag) {
					InputTag it = (InputTag)nd;
					value = it.getAttribute("value");
				}
				nd = nodesChildren.elementAt(i+1);
				if (nd instanceof TextNode) {
					text = nd.getText().replaceAll("&nbsp;", "");
					// System.out.println(FormParser.class.getName() + " " + nd.getClass() + " " + text);					
				}
				ary[i/2][0] = value;
				ary[i/2][1] = text;
			}			
		} catch (ParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return ary;
	}
	
	/**
	 * 取得同组的checkbox，其description均相同
	 * @param fd
	 * @param ff
	 * @return
	 */
	public static String[][] getOptionsArrayOfCheckbox(FormDb fd, FormField ff) {
		String[][] ary = new String[1][3];
		String desc = StrUtil.getNullStr(ff.getDescription());
		if ("".equals(desc)) {
			ary[0][0] = "1";
			ary[0][1] = ff.getName();
			ary[0][2] = ff.getTitle();
			return ary;			
		}
		String content = fd.getContent();
		Parser parser;
		try {
			parser = new Parser(content);
			parser.setEncoding("utf-8");//
			AndFilter filter = new AndFilter(new TagNameFilter("input"),
					new HasAttributeFilter("description", ff.getDescription()));
			NodeList nodes = parser.parse(filter);//
			
			if (nodes == null || nodes.size() == 0) {
				ary = new String[0][0];
				return ary;
			}
			
			int len = nodes.size();
			ary = new String[len][3];			
			for (int i=0; i<len; i++) {
				Node nd = nodes.elementAt(i);
				if (nd instanceof InputTag) {
					InputTag it = (InputTag)nd;					
					ary[i][0] = "1";
					ary[i][1] = it.getAttribute("name");
					ary[i][2] = it.getAttribute("title");
				}
			}		
		} catch (ParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return ary;
	}	

	/**
	 * 取得表单中Select选择框的Options
	 * 
	 * @param fd
	 *            FormDb
	 * @param ff
	 *            FormField
	 * @return String
	 */
	public static String getOptionsOfSelect(FormDb fd, FormField ff) {
		String content = fd.getContent();
		Parser parser;
		String options = "";
		try {
			parser = new Parser(content);
			parser.setEncoding("utf-8");//
			AndFilter filter = new AndFilter(new TagNameFilter("select"),
					new HasAttributeFilter("name", ff.getName()));
			NodeList nodes = parser.parse(filter);//
			
			if (nodes == null || nodes.size() == 0) {
				return options;
			}

			Node node = nodes.elementAt(0);
			NodeList nodesChild = node.getChildren();
			options = nodesChild.toHtml();
		} catch (ParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return options;
	}

	public static String getOptionText(String[][] optionAry, String optionVal) {
		for (int i = 0; i < optionAry.length; i++) {
			if (optionAry[i][1].equals(optionVal))
				return optionAry[i][0];
		}
		return "";
	}

	/**
	 * 返回下拉菜单的项，数组中第一个为text，第二个为value，用于传值给手机端
	 * 
	 * @param fd
	 *            FormDb
	 * @param ff
	 *            FormField
	 * @return String[][]
	 */
	public static String[][] getOptionsArrayOfSelect(FormDb fd, FormField ff) {
		String content = fd.getContent();
		Parser parser;
		try {
			parser = new Parser(content);
			parser.setEncoding("utf-8");//
			AndFilter filter = new AndFilter(new TagNameFilter("select"),
					new HasAttributeFilter("name", ff.getName()));
			NodeList nodes = parser.parse(filter);//
			
			if (nodes == null || nodes.size() == 0) {
				return new String[0][2];
			}

			Node node = nodes.elementAt(0);
			NodeList nodesChild = node.getChildren();
			String[][] ary = new String[nodesChild.size()][2];			
			for (int i=0; i<nodesChild.size(); i++) {
				OptionTag tag = (OptionTag) nodesChild.elementAt(i);
				ary[i][0] = StringEscapeUtils.unescapeHtml3(tag.getOptionText());
				ary[i][1] = tag.getValue();
			}
			return ary;
		} catch (ParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new String[0][2];
	}

	/**
	 * 返回input输入框的值（含单选按钮及checkbox），用于手机端取值
	 * 
	 * @param fd
	 *            FormDb
	 * @param ff
	 *            FormField
	 * @return String[]
	 */
	public static String[] getValuesOfInput(FormDb fd, FormField ff) {
		String content = fd.getContent();
        String[] ary = new String[0];
        Parser parser;
        try {
            parser = new Parser(content);
            parser.setEncoding("utf-8");//
            AndFilter filter = new AndFilter(new TagNameFilter("input"),
                    new HasAttributeFilter("name", ff.getName()));
            NodeList nodes = parser.parse(filter);//
            if (nodes == null || nodes.size() == 0) {
                return ary;
            }

            int len = nodes.size();
            ary = new String[len];
            for (int i=0; i<len; i++) {
                Node nd = nodes.elementAt(i);
                InputTag it = (InputTag)nd;
                ary[i] = it.getAttribute("value");
            }
        } catch (ParserException e) {
            e.printStackTrace();
        }
        return ary;
	}

	/**
	 * 将视图中的input控件转换成主表单中的控件格式
	 * 
	 * @param content
	 * @param ieVersion
	 * @return
	 */
	public String generateView(String content, String ieVersion, String formCode) {
		FormDb fd = new FormDb();
		fd = fd.getFormDb(formCode);

		Vector v = parseCtlFromView(content, ieVersion, fd);

		Iterator ir = v.iterator();
		while (ir.hasNext()) {
			FormField ff = (FormField) ir.next();

			// ff.setType(fd.getFormField(ff.getName()).getType());
			// ff = fd.getFormField(ff.getName());
			String ctlHTML = getFieldCtlHTML(ff, fd.getContent(), fd
					.getIeVersion());
			content = replaceCtlForView(content, ieVersion, ff.getName(),
					ctlHTML);
		}
		return content;
	}

	/**
	 * 取得表单域的属性，用于取出计算控件的属性：精度、是否四舍五入发给手机端
	 * 
	 * @param fd
	 * @param ff
	 * @param attName
	 * @return
	 */
	public String getFieldAttribute(FormDb fd, FormField ff, String attName) {
		String ctlHTML = getFieldCtlHTML(ff, fd.getContent(), fd.getIeVersion());
		return getAttribute(attName, ctlHTML);
	}

	/**
	 * 将视图中的input控件替换为主表单中实际的控件html
	 * 
	 * @param content
	 * @param fieldName
	 * @param ctlHTML
	 * @return
	 */
	public String replaceCtlForView(String content, String ieVersion,
			String fieldName, String ctlHTML) {
		Parser parser;
		try {
			parser = new Parser(content);
			parser.setEncoding("utf-8");//
			AndFilter filter = new AndFilter(new TagNameFilter("input"),
					new HasAttributeFilter("name", fieldName));
			NodeList nodes = parser.parse(filter);//
			
			if (nodes == null || nodes.size() == 0) {
				return content;
			}

			Node node = nodes.elementAt(0);
			// node.setText(ctlHTML);
			int s = node.getStartPosition();
			int e = node.getEndPosition();
			
			String c = content.substring(0, s);
			c += ctlHTML;
			c += content.substring(e);
			return c;
		} catch (ParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
		return content;
	}

	/**
	 * 从视图中解析出所有的字段
	 * 
	 * @param content
	 * @param ieVersion
	 * @param fd
	 * @return
	 */
	public Vector parseCtlFromView(String content, String ieVersion, FormDb fd) {
		Vector v = new Vector();
		Parser parser;
		try {
			parser = new Parser(content);
			NodeList nodes = parser.extractAllNodesThatMatch(new NodeClassFilter(InputTag.class));
			for (int i = 0; i < nodes.size(); i++) {
				InputTag tag = (InputTag) nodes.elementAt(i);								
				FormField ff = new FormField();
				ff.setName(tag.getAttribute("name"));
				ff.setTitle(tag.getAttribute("title"));
				ff = fd.getFormField(ff.getName());
	
				// 查找fieldType
				if ("1".equals(tag.getAttribute("mode"))) {
					ff.setEditable(true);
				} else {
					ff.setEditable(false);
				}
				v.addElement(ff);				
			}			
		} catch (ParserException e) {
			e.printStackTrace();
		}
		return v;
	}

	/**
	 * 从主表单中得到控件的html
	 * 
	 * @param ff
	 * @param content
	 * @return
	 */
	public String getFieldCtlHTML(FormField ff, String content, String ieVersion) {
		if (ff.getType().equals(FormField.TYPE_SELECT)) {
			return getSelectCtl(ff.getName(), content, ieVersion);
		} else if (ff.getType().equals(FormField.TYPE_TEXTAREA)) {
			return getTextareaCtl(ff.getName(), content, ieVersion);
		} else if (ff.getType().equals(FormField.TYPE_LIST)) {
			return getListCtl(ff.getName(), content, ieVersion);
		} else {
			return getTextfieldCtl(ff.getName(), content, ieVersion);
		}
	}

	public String getTextfieldCtl(String fieldName, String content,
			String ieVersion) {
        Parser parser;
        String ctl = "";
        try {
            parser = new Parser(content);
            parser.setEncoding("utf-8");//
            AndFilter filter = new AndFilter(new TagNameFilter("input"),
                    new HasAttributeFilter("name", fieldName));
            NodeList nodes = parser.parse(filter);//
            if (nodes == null || nodes.size() == 0) {
                return ctl;
            }

            Node node = nodes.elementAt(0);
            ctl = node.toHtml();
        } catch (ParserException e) {
            e.printStackTrace();
        }
        return ctl;
    }

	public String getSelectCtl(String fieldName, String content,
			String ieVersion) {
		Parser parser;
		String ctl = "";
		try {
			parser = new Parser(content);
			parser.setEncoding("utf-8");//
			AndFilter filter = new AndFilter(new TagNameFilter("select"),
					new HasAttributeFilter("name", fieldName));
			NodeList nodes = parser.parse(filter);//
			
			if (nodes == null || nodes.size() == 0) {
				return ctl;
			}

			Node node = nodes.elementAt(0);
			ctl = node.toHtml();
		} catch (ParserException e) {
			e.printStackTrace();
		}
		
		return ctl;
	}

	public String getTextareaCtl(String fieldName, String content,
			String ieVersion) {
		Parser parser;
		String ctl = "";
		try {
			parser = new Parser(content);
			parser.setEncoding("utf-8");//
			AndFilter filter = new AndFilter(new TagNameFilter("textarea"),
					new HasAttributeFilter("name", fieldName));
			NodeList nodes = parser.parse(filter);//
			if (nodes == null || nodes.size() == 0) {
				return ctl;
			}

			Node node = nodes.elementAt(0);
			ctl = node.toHtml();
		} catch (ParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ctl;
	}

	public String getListCtl(String fieldName, String content, String ieVersion) {
        return getSelectCtl(fieldName, content, ieVersion);
	}

}
