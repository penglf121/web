package com.callke8.common;

import java.util.Enumeration;
import java.util.List;

import com.callke8.system.operationlog.OperationLog;
import com.callke8.utils.BlankUtils;
import com.callke8.utils.DateFormatUtils;
import com.callke8.utils.MemoryVariableUtil;
import com.callke8.utils.StringUtil;
import com.jfinal.aop.Interceptor;
import com.jfinal.core.ActionInvocation;
import com.jfinal.core.Controller;
import com.jfinal.plugin.activerecord.Record;

/**
 * 公共拦截器，用于判断登录用户是否失效，及登记操作日志
 * @author Administrator
 *
 */
public class CommonInterceptor implements Interceptor {

	@Override
	public void intercept(ActionInvocation ai) {
		Controller controller = ai.getController();
 		
		//登录用户的检查
		String currOperId = controller.getSessionAttr("currOperId");
		if(!BlankUtils.isBlank(currOperId)) {     //登录信息有效...
			ai.invoke();
		}else if(controller.getClass().getSimpleName().equals("AutoContactController")){
			ai.invoke();
		}else if(controller.getClass().getSimpleName().equals("BSHOrderListController") && ai.getMethodName().equalsIgnoreCase("add")){   //如果博世家电向系统提交数据时，无需登录也可以提交数据
			ai.invoke();
		}else if(controller.getClass().getSimpleName().equals("AutoCallTaskController") && ai.getMethodName().equalsIgnoreCase("createSelfTask")) {
			ai.invoke();
		}else if(controller.getClass().getSimpleName().equals("AutoCallTaskController") && ai.getMethodName().equalsIgnoreCase("getResult")) {
			ai.invoke();
		}else {                                   //登录信息失效...
			controller.forwardAction("/index");
		}
		
		//创建操作日志
		String methodName = ai.getMethodName();    //方法名字
		//不空时，且为一定的方法时，才创建操作日志：datagrid(查询);update(修改);add(增加);delete(删除);export(导出)
		if(!BlankUtils.isBlank(methodName)) {      //方法名字不能为空，且需要在数据字典里的操作状态里
			if(checkOperationType(methodName)) {
				//如果经过用户的检查后，进行操作信息登记
				Record operationLog = createOperationLog(ai);    //创建一个操作信息
				
				boolean b = OperationLog.dao.add(operationLog);
				if(b) {
					System.out.println("操作日志插入成功!操作日志对象:" + operationLog);
				}else {
					System.out.println("操作日志插入失败!操作日志对象:" + operationLog);
				}
			}
		}
		
	}
	
	/**
	 * 根据方法的名字，检查是否在数据字典中已经配置了操作日志的类型
	 * @param methodName
	 * @return
	 */
	public boolean checkOperationType(String methodName) {
		boolean b = false;
		//根据OPERATION_TYPE，取出操作日志类型的所有类型，如add、update、delete等等
		List<Record> list = MemoryVariableUtil.dictMap.get("OPERATION_TYPE");
		
		for(Record r:list) {
			String dictCode = r.get("DICT_CODE");
			
			if(dictCode.equalsIgnoreCase(methodName)) {
				b = true;
				break;
			}
		}
		return b;
	}
	
	/**
	 * 创建一个操作日志对象
	 * 
	 * @param controller
	 * @return
	 */
	public Record createOperationLog(ActionInvocation ai) {
		
		//System.out.println("ai========:" + ai);
		
		Controller controller = ai.getController();
		Record operationLog = new Record();
		
		//(1)操作人ID
		String currOperId = controller.getSessionAttr("currOperId");
		operationLog.set("OPER_ID", currOperId);    //操作人ID
		//(2)IP地址信息
		operationLog.set("IP_ADDRESS",controller.getRequest().getRemoteAddr());      //IP地址
		//(3)操作时间
		operationLog.set("OPERATION_TIME",DateFormatUtils.getDate());         //操作时间
		
		//(4)参数列表
		Enumeration<String> pns = controller.getParaNames();
		StringBuilder sb = new StringBuilder();    //定义参数builder,用于设置参数
		while(pns.hasMoreElements()) {
			String pn = pns.nextElement();
			sb.append(pn + "=");
			sb.append(controller.getPara(pn));
			sb.append("|");
		}
		operationLog.set("PARAMS",sb.toString());     //设置参数集
		
		//(5)菜单编码
		String controllerKey = ai.getControllerKey();    //获取 controllerKey，格式大概是  /clientInfo   /callTask  这样的类型
		if(!BlankUtils.isBlank(controllerKey) && StringUtil.containsAny(controllerKey, "/")) {   //如果不为空，且包括/时，需要将/删除
			controllerKey = controllerKey.replace("/", "");
		}
		
		operationLog.set("MODULE_CODE",MemoryVariableUtil.getModuleCode(controllerKey));		//菜单编码
		
		
		//(6)操作类型
		String operation = ai.getMethodName();
		operationLog.set("OPERATION", operation);											  //操作类型
		
		return operationLog;
	}

}
