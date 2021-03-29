package kr.dja.raspberryClock;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

public class SerialCommunicator
{
	private final String targetName;
	private final int baudRate;
	
	private final SerialPort serialPort;

	public SerialCommunicator(String targetName, int baudRate)
	{
		this.targetName = targetName;
		this.baudRate = baudRate;

		this.serialPort = this._openSerialPort(targetName, baudRate);

		this._bindSerialReadEvent(this.serialPort);

	}

	private SerialPort _openSerialPort(String targetName, int boudRate)
	{
		System.out.println("\nUsing Library Version v" + SerialPort.getVersion());
		SerialPort[] ports = SerialPort.getCommPorts();
		System.out.println("\nAvailable Ports:\n");
		for (int i = 0; i < ports.length; ++i)
			System.out.println("   [" + i + "] " + ports[i].getSystemPortName() + ": "
					+ ports[i].getDescriptivePortName() + " - " + ports[i].getPortDescription());

		SerialPort ubxPort = SerialPort.getCommPort(targetName);

		System.out.println("\nPre-setting RTS: " + (ubxPort.setRTS() ? "Success" : "Failure"));
		boolean openedSuccessfully = ubxPort.openPort(0);
		System.out.println("\nOpening " + ubxPort.getSystemPortName() + ": " + ubxPort.getDescriptivePortName() + " - "
				+ ubxPort.getPortDescription() + ": " + openedSuccessfully);

		ubxPort.setBaudRate(boudRate);

		return ubxPort;
	}

	private void _bindSerialReadEvent(SerialPort port)
	{
		port.addDataListener(new SerialPortDataListener()
		{

			@Override
			public int getListeningEvents()
			{
				return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
			}

			@Override
			public void serialEvent(SerialPortEvent event)
			{
				try
				{
					SerialPort comPort = event.getSerialPort();
					byte[] newData = new byte[comPort.bytesAvailable()];
					comPort.readBytes(newData, newData.length);

				}
				catch (Exception e)
				{
					e.printStackTrace();
				}

			}
		});
	}
	
	
	public int writeToSerial(byte[] data)
	{
		int result = -1;
		
		
		if (this.serialPort != null)
		{
			result = this.serialPort.writeBytes(data, data.length);
		}

		return result;
	}
	
	public void close()
	{
		this.serialPort.closePort();
	}
}
