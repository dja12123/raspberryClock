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
	private ScheduledExecutorService exeService;
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
		this.exeService = Executors.newSingleThreadScheduledExecutor();
		this.timeCorrection();
		this.exeService.scheduleWithFixedDelay(this::displayTemperatureOnLCD, 0, 500, TimeUnit.MILLISECONDS);
	}
	
	private void timeCorrection()
	{
		if(this.printTimeTask != null) this.printTimeTask.cancel(false);
		LocalDateTime now = LocalDateTime.now();
		this.displayTime = now;
		this.displayDate = now.toLocalDate();
		int secondLeftMs = (1000000000 - now.getNano()) /1000000;
		this.printTimeTask = this.exeService.scheduleAtFixedRate(this::displayTimeOnLCD, secondLeftMs, 1000, TimeUnit.MILLISECONDS);
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
		System.out.print("\33[1A\33[2K");
		System.out.println(now);
	}

	
	private void displayTemperatureOnLCD()
	{
		double temperature = this.readTemperatureMLX90614();
		String printString = String.format("%.2f°C", temperature);
		if(!printString.equals(this.beforePrintTemperature))
		{
			this.sendDataToNX4827T043_011("page0.Temp.txt", printString);
			this.beforePrintTemperature = printString;
		}
	}
	
	private double readTemperatureMLX90614()
	{
		byte[] buf = new byte[2];
		try
		{
			this.tempDevice.read(0x07, buf, 0, 2);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		int rawValue = (buf[0] & 0xff) << 8 | (buf[1] & 0xff);
		double temperature = rawValue * 0.02 - 273.15;
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
		this.exeService.shutdownNow();
		this.comm.close();
	}
}
