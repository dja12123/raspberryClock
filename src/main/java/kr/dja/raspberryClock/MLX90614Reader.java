package kr.dja.raspberryClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MLX90614Reader
{
	public MLX90614Reader()
	{
		
	}
	
	public double readTemperature()
	{
		String rawStr;
		try
		{
			rawStr = this.executeCommand("echo `i2cget -y 1 0x5a 0x07 w`");
		}
		catch(Exception e)
		{
			
			e.printStackTrace();
			return 0;
		}
		int rawValue = Integer.parseInt(rawStr.substring(2, 6), 16);
		double temperature = rawValue * 0.02 - 273.15;
		return temperature;
	}

	private String executeCommand(String cmd) throws Exception
	{
		BufferedReader successBufferReader=null;
		String msg=null;
		StringBuffer resultMsg=new StringBuffer();

		Runtime runtime=Runtime.getRuntime();

		Process process=runtime.exec(cmd);
		successBufferReader=new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
		while((msg=successBufferReader.readLine())!=null)
		{
			resultMsg.append(msg);
		}

		process.waitFor();

		process.destroy();
		if(successBufferReader!=null)
		{
			successBufferReader.close();
		}
		return resultMsg.toString();
	}
}
