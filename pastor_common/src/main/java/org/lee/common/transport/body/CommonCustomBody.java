package org.lee.common.transport.body;


import org.lee.common.exception.RemotingCommmonCustomException;

/**
 * 
 * @author BazingaLyn
 * @description 网络传输对象的主体对象
 * @time 2016年8月10日
 * @modifytime
 */
public interface CommonCustomBody {
	
    void checkFields() throws RemotingCommmonCustomException;
}
