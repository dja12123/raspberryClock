package kr.dja.raspberryClock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
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
	private ScheduledExecutorService clockExeService;
	private ScheduledExecutorService sensorExeService;
	private I2CDevice tempDevice;
	
	private ScheduledFuture<?> printTimeTask;
	private LocalDateTime displayTime;
	private LocalDate displayDate;
	private String beforePrintTemperature;
	
	Core()
	{
        try
		{
    		I2CBus i2c = I2CFactory.getInstance(I2CBus.BUS_1);
			this.tempDevice = i2c.getDevice(0x5A);
		}
		catch(IOException | UnsupportedBusNumberException e)
		{
			e.printStackTrace();
		}
		
		this.comm=new SerialCommunicator("ttyS0", 9600);
		this.clockExeService = Executors.newSingleThreadScheduledExecutor();
		this.sensorExeService = Executors.newSingleThreadScheduledExecutor();
		this.timeCorrection();
		this.sensorExeService.scheduleWithFixedDelay(this::displayTemperatureOnLCD, 0, 500, TimeUnit.MILLISECONDS);
	}
	
	private void timeCorrection()
	{
		if(this.printTimeTask != null) this.printTimeTask.cancel(false);
		LocalDateTime now = LocalDateTime.now();
		this.displayTime = now;
		this.displayDate = now.toLocalDate();
		int secondLeftMs = (1000000000 - now.getNano()) /1000000;
		this.printTimeTask = this.clockExeService.scheduleAtFixedRate(this::displayTimeOnLCD, secondLeftMs, 1000, TimeUnit.MILLISECONDS);
	}
	
	private void displayTimeOnLCD()
	{
		LocalDateTime now = this.displayTime.plusSeconds(1);
		this.displayTime = now;
		String time = timeFormat.format(now);
		this.sendDataToNX4827T043_011("page0.Time.txt", time);
		LocalDate nowDate = now.toLocalDate();
		if(!nowDate.equals(this.displayDate))
		{
			this.displayDate = nowDate;
			String date = dateFormat.format(now);
			String week = weekKor.get(now.getDayOfWeek().getValue() - 1);
			this.sendDataToNX4827T043_011("page0.Date.txt", date+"("+week+")");
			this.timeCorrection();
		}
		System.out.print("\33[1;1f\33[K");
		System.out.print(now);
	}

	
	private void displayTemperatureOnLCD()
	{
		double sum = 0;
		for(int i = 0; i < 1; ++i)
		{
			sum += this.readTemperatureMLX90614();
			try
			{
				Thread.sleep(10);
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		double temperature = sum / 1;
		String printString = String.format("%.2f°C", temperature);
		if(!printString.equals(this.beforePrintTemperature))
		{
			this.sendDataToNX4827T043_011("page0.Temp.txt", printString);
			this.beforePrintTemperature = printString;
		}
	}
	
	private double readTemperatureMLX90614()
	{
		byte[] buf = new byte[3];
		buf[0] = 0x06;
		try
		{
			this.tempDevice.read(buf, 0, 1, buf, 0, 3);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		int rawValue = (buf[1] & 0xff) << 8 | (buf[0] & 0xff);
		double temperature = rawValue * 0.02 - 273.15;
		System.out.print("\33[2;1f\33[K");
		System.out.print(temperature + " " + buf[0] + " " + buf[1]);
		return temperature;
		
	}
	
	public void sendDataToNX4827T043_011(String field, String var)
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
		this.clockExeService.shutdownNow();
		this.comm.close();
	}
}
