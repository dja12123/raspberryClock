package kr.dja.raspberryClock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

public class Core
{
	static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
	static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH시 mm분 ss초");
	static final List<String> weekKor = List.of("월", "화", "수", "목", "금", "토", "일");
	
	public static void main(String[] args)
	{
		Core core = new Core();
		
		//core.close();
	}
	
	private SerialCommunicator comm;
	private ScheduledExecutorService exeService;
	private I2CDevice tempDevice;
	
	private ScheduledFuture<?> printTimeTask;
	private String beforePrintTemperature;
	
	Core()
	{
        try
		{
    		I2CBus i2c = I2CFactory.getInstance(I2CBus.BUS_1);
			this.tempDevice = i2c.getDevice(0x48);
		}
		catch(IOException | UnsupportedBusNumberException e)
		{
			e.printStackTrace();
		}
		
		this.comm=new SerialCommunicator("ttyS0", 9600);
		this.exeService = Executors.newSingleThreadScheduledExecutor();
		this.printDate();
		this.exeService.scheduleWithFixedDelay(this::printTemperature, 0, 500, TimeUnit.MILLISECONDS);
	}
	private void printTime()
	{
		LocalDateTime now = LocalDateTime.now();
		String time = timeFormat.format(now);
		this.printValue("page0.Time.txt", time);
		System.out.print("\33[1A\33[2K");
		System.out.println(now);
	}
	
	private void printDate()
	{
		if(this.printTimeTask != null) this.printTimeTask.cancel(false);
		LocalDateTime now = LocalDateTime.now();
		long dayLeftMs = Duration.between(now ,now.toLocalDate().plusDays(1).atStartOfDay()).toMillis();
		int secondLeftMs = (1000000000 - now.getNano()) /1000000;
		this.printTimeTask = this.exeService.scheduleAtFixedRate(this::printTime, secondLeftMs + 1002, 1000, TimeUnit.MILLISECONDS);
		String date = dateFormat.format(now);
		String week = weekKor.get(now.getDayOfWeek().getValue() - 1);
		this.exeService.schedule(this::printDate, dayLeftMs + 2, TimeUnit.MILLISECONDS);
		this.printValue("page0.Date.txt", date+"("+week+")");
		this.printTime();
	}
	
	private void printTemperature()
	{
		byte[] buf = new byte[2];
		try
		{
			this.tempDevice.read(0x00, buf, 0, 2);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		int rawValue = (buf[0] & 0xff) << 8 | (buf[1] & 0xff);
		double temperature = rawValue * 0.00390625;
		String printString = String.format("%.2f°C", temperature);
		if(!printString.equals(this.beforePrintTemperature))
		{
			this.printValue("page0.Temp.txt", printString);
		}
	}
	
	public void printValue(String field, String var)
	{
		byte[] strbyte = (field+"=\""+var+"\"").getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(strbyte.length + 3);
		buf.put(strbyte);
		buf.put((byte)0xFF);
		buf.put((byte)0xFF);
		buf.put((byte)0xFF);
		byte[] payload = buf.array();
		this.comm.writeToSerial(payload);
	}
	
	public void close()
	{
		this.exeService.shutdownNow();
		this.comm.close();
	}
}
