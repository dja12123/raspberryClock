package kr.dja.raspberryClock;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


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
	private MLX90614Reader temperatureReader;
	
	private ScheduledFuture<?> printTimeTask;
	private LocalDateTime displayTime;
	private LocalDate displayDate;
	private String beforePrintTemperature;
	
	Core()
	{
		this.temperatureReader = new MLX90614Reader();
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
		this.displayDateOnLCD(this.displayDate);
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
			this.displayDateOnLCD(nowDate);
			this.timeCorrection();
		}
		System.out.print("\33[1;1f\33[K");
		System.out.print(now);
	}
	
	private void displayDateOnLCD(LocalDate nowDate)
	{
		this.displayDate = nowDate;
		String date = dateFormat.format(nowDate);
		String week = weekKor.get(nowDate.getDayOfWeek().getValue() - 1);
		this.sendDataToNX4827T043_011("page0.Date.txt", date+"("+week+")");
	}

	
	private void displayTemperatureOnLCD()
	{
		double sum = 0;
		for(int i = 0; i < 10; ++i)
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
		double temperature = sum / 10;
		String printString = String.format("%.2f°C", temperature);
		if(!printString.equals(this.beforePrintTemperature))
		{
			this.sendDataToNX4827T043_011("page0.Temp.txt", printString);
			this.beforePrintTemperature = printString;
		}
	}
	
	private double readTemperatureMLX90614()
	{
		
		double temperature = this.temperatureReader.readTemperature();
		System.out.print("\33[2;1f\33[K");
		System.out.print("temperature:" + temperature);
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
