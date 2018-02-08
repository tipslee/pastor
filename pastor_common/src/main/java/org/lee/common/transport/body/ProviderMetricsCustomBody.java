package org.lee.common.transport.body;


import org.lee.common.exception.remoting.RemotingCommmonCustomException;
import org.lee.common.rpc.MetricsReporter;

import java.util.List;

/**
 * 
 * @author BazingaLyn
 * @description 管理员发送给监控中心的信息
 * @time 2016年9月1日
 * @modifytime
 */
public class ProviderMetricsCustomBody implements CommonCustomBody {
	
	
	private List<MetricsReporter> metricsReporter;

	@Override
	public void checkFields() throws RemotingCommmonCustomException {
	}

	public List<MetricsReporter> getMetricsReporter() {
		return metricsReporter;
	}

	public void setMetricsReporter(List<MetricsReporter> metricsReporter) {
		this.metricsReporter = metricsReporter;
	}
	

}
