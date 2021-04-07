package kr.dja.raspberryClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MLX90614Reader
{
	private Runtime runtime;
	public MLX90614Reader()
	{
		this.runtime=Runtime.getRuntime();
	}
	
	public double readTemperature()
	{
		String rawStr;
		try
		{
			rawStr = this.executeCommand("/usr/sbin/i2cget -y 1 0x5a 0x07 w");
		}
		catch(Exception e)
		{
			
			e.printStackTrace();
			return 0;
		}
		int rawValue;
		try
		{
			rawValue = Integer.parseInt(rawStr.substring(2, 6), 16);
		}
		catch(NumberFormatException e)
		{
			e.printStackTrace();
			return 0;
		}
		double temperature = rawValue * 0.02 - 273.15;
		return temperature;
	}

	private String executeCommand(String cmd) throws Exception
	{
		String msg=null;
		StringBuffer resultMsg=new StringBuffer();

		Process process=this.runtime.exec(cmd);
		process.waitFor();
		BufferedReader successBufferReader=new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
		BufferedReader errorBufferReader=new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"));
		while((msg=successBufferReader.readLine())!=null)
		{
			resultMsg.append(msg);
		}
		
		while((msg=errorBufferReader.readLine())!=null)
		{
			
		}
		process.waitFor();
		successBufferReader.close();
		return resultMsg.toString();
	}
}
