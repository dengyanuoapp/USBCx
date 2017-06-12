package org.ykc.usbcx;

import java.io.IOException;
import java.util.ArrayList;

import javax.usb.UsbDevice;
import javax.usb.UsbException;

import org.controlsfx.control.StatusBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ykc.usbmanager.USBManEvent;
import org.ykc.usbmanager.USBManListener;
import org.ykc.usbmanager.USBManager;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class USBControl implements USBManListener{
	public static final Logger logger = LoggerFactory.getLogger(USBControl.class.getName());
	private boolean isUSBControllerStarted = false;
	private boolean isHwAttached = false;
	private boolean isHwCapturing = false;
	private ArrayList<UsbDevice> devList;
	private USBManager usbm;
	private UsbDevice dev;
	private int comboBoxDeviceSelIdx = -1;
	static private USBTransfer usbTransferTask;
	private ComboBox<String> cBoxDeviceList;
	private StatusBar statusBar;


	public boolean isHwCapturing() {
		return isHwCapturing;
	}

	public static USBTransfer getUsbTransferTask() {
		return usbTransferTask;
	}

	public USBControl(ComboBox<String> cBoxDeviceList, StatusBar statusBar) {
		try {
			usbm = USBManager.getInstance();
			usbTransferTask = new USBTransfer();

			this.statusBar = statusBar;
			this.cBoxDeviceList = cBoxDeviceList;

			updateDeviceList();

			cBoxDeviceList.setOnAction((event) -> {
			    deviceSelectionChanged();
			});

			if(!devList.isEmpty()){
				cBoxDeviceList.getSelectionModel().clearAndSelect(0);
				deviceSelectionChanged();
			}

			usbm.addUSBManListener(this);

			Thread usbTransferThread = new Thread(usbTransferTask);
			usbTransferThread.start();

			isUSBControllerStarted = true;

		} catch (SecurityException e) {
			logger.error("Error in creating USB manager");
		} catch (UsbException e) {
			logger.error("Error in creating USB manager");
		}
	}

	private void deviceSelectionChanged() {
		int selIdx = cBoxDeviceList.getSelectionModel().getSelectedIndex();

		if((selIdx >= 0) &&(selIdx != comboBoxDeviceSelIdx))
		{
			comboBoxDeviceSelIdx = selIdx;
			dev = devList.get(comboBoxDeviceSelIdx);
			usbTransferTask.setDevice(dev);
			statusBar.setText("Device Selected : " + dev.toString() + " -> " + "USBCx HW Attached");
			isHwAttached = true;
			logger.info("USBCx HW attached: " + dev.toString());
		}

	}

	private void updateDeviceList()
	{
		cBoxDeviceList.getItems().clear();
		devList = usbm.getDeviceList(USBTransfer.VID, USBTransfer.PID);
		for(UsbDevice device : devList)
		{
			cBoxDeviceList.getItems().add(device.toString());
			if(!isHwAttached){
				cBoxDeviceList.getSelectionModel().select(0);
			}
		}
	}

	@Override
	public void deviceAttached(USBManEvent arg0) {
		Platform.runLater(() -> {
			updateDeviceList();
        });
	}

	@Override
	public void deviceDetached(USBManEvent e) {
		Platform.runLater(() -> {
			if(isHwAttached == true)
			{
				if(USBManager.isDevicePresent(USBManager.filterDeviceList(e.getDeviceList(), USBTransfer.VID, USBTransfer.PID), dev) == false)
				{
					usbTransferTask.pause();
					isHwAttached = false;
					isHwCapturing = false;
					statusBar.setText("USBCx HW Removed \n");
					comboBoxDeviceSelIdx = -1;
				}
			}
			updateDeviceList();
        });
	}

	public void sendStartCommand(){
		if(isHwAttached == false){
			statusBar.setText("USBCx HW not attached: Command failed.");
			return;
		}
		Boolean result = usbTransferTask.start(false, 8000);
		if(result == true)
		{
			isHwCapturing = true;
			usbTransferTask.resume();
			statusBar.setText("Start Command Success.");
		}
		else
		{
			statusBar.setText("Start Command fail. -> Please read the user guide for linux/windows driver details.");
		}
	}

	public void sendStopCommand(){
		if(isHwAttached == false){
			statusBar.setText("USBCx HW not attached: Command failed.");
			return;
		}
		usbTransferTask.pause();

		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
		}

		Boolean result = usbTransferTask.stop();
		if(result == true)
		{
			isHwCapturing = false;
			statusBar.setText("Stop Command Success.");
		}
		else
		{
			statusBar.setText("Stop Command fail. -> Please read the user guide for linux/windows driver details.");
		}
	}

	public void startStopCapture() {
    	if(isHwCapturing){
    		sendStopCommand();
    	}
    	else{
    		sendStartCommand();
    	}
	}

	public void resetCapture(){
		if(isHwAttached == false){
			statusBar.setText("USBCx HW not attached: Command failed.");
			return;
		}
		sendStopCommand();
		usbTransferTask.resetQueue();
		sendStartCommand();

	}

	public void getVersion() {
		if(isHwAttached == false){
			statusBar.setText("USBCx HW not attached: Command failed.");
			return;
		}
		byte[] ver = new byte[4];
		boolean result;
		result = usbTransferTask.getVersion(ver);
		if(result == true)
		{
			statusBar.setText("Version read success: " +
					Utils.getUnsignedInt(ver[3]) + "." +
					Utils.getUnsignedInt(ver[2]) + "." +
					Utils.getUnsignedInt(ver[1]) + "." +
					Utils.getUnsignedInt(ver[0]));
		}
		else
		{
			statusBar.setText("Version read fail.");
		}
	}

	public void downloadFW() {
		if(isHwAttached == false){
			statusBar.setText("USBCx HW not attached: Command failed.");
			return;
		}

		boolean result;
		result = usbTransferTask.putDeviceInBootloadMode();
		if(result == true)
		{
			statusBar.setText("Device in Bootload mode.");
			try {
				Process process = new ProcessBuilder(".\\BootloaderHost\\USBBootloaderHost.exe").start();
			} catch (IOException e) {

				statusBar.setText("Failed to find bootload exe.");
			}
			catch(Exception e)
			{
				statusBar.setText("Failed to run Exe.");
			}
		}
		else
		{
			statusBar.setText("BootLoad Entry failed.");
		}
	}

	public void setTrigger(CheckBox chkStartSno, CheckBox chkEndSno, CheckBox chkSop,
			CheckBox chkMsgType, CheckBox chkCount, CheckBox chkMsgId, TextField txtStartSno, TextField txtEndSno,
			ComboBox<String> cBoxSop, ComboBox<String> cBoxMsgClass, ComboBox<String> cBoxMsgType, TextField txtCount,
			TextField txtMsgId) {

		if(isHwAttached == false){
			statusBar.setText("USBCx HW not attached: Command failed.");
			return;
		}

		try{
	        byte[] cmd = new byte[64];
	        cmd[0] = 2;


	        if (chkStartSno.isSelected())
	        {
	        	cmd[4] = 1;
	        }

	        if (chkEndSno.isSelected())
	        {
	        	cmd[5] = 1;
	        }

	        if (chkSop.isSelected())
	        {
	        	cmd[6] = 1;
	        }

	        if (chkMsgType.isSelected())
	        {
	        	cmd[7] = 1;
	        }

	        if (chkCount.isSelected())
	        {
	        	cmd[8] = 1;
	        }

	        if (chkMsgId.isSelected())
	        {
	        	cmd[9] = 1;
	        }

	        cmd[10] = (byte)cBoxMsgType.getSelectionModel().getSelectedIndex();
	        cmd[11] = (byte)cBoxMsgClass.getSelectionModel().getSelectedIndex();

	        cmd[12] = (byte)cBoxSop.getSelectionModel().getSelectedIndex();
	        cmd[13] = (byte)Integer.parseInt(txtCount.getText());
	        cmd[14] = (byte)Integer.parseInt(txtMsgId.getText());

	        Long start_sno = Long.parseLong(txtStartSno.getText());
	        cmd[16] = Utils.uint32_get_b0(start_sno);
	        cmd[17] = Utils.uint32_get_b1(start_sno);
	        cmd[18] = Utils.uint32_get_b2(start_sno);
	        cmd[19] = Utils.uint32_get_b3(start_sno);

	        Long end_sno = Long.parseLong(txtEndSno.getText());
	        cmd[20] = Utils.uint32_get_b0(end_sno);
	        cmd[21] = Utils.uint32_get_b1(end_sno);
	        cmd[22] = Utils.uint32_get_b2(end_sno);
	        cmd[23] = Utils.uint32_get_b3(end_sno);

	        /* Send trigger command */
	        if (usbTransferTask.setTigger(cmd) == true)
	        {
	        	statusBar.setText("Trigger Set Successful.");
	        }
	        else
	        {
	        	statusBar.setText("Trigger Set Failed. Please check settings.");
	        }
	    }
	    catch (Exception ex)
	    {
	    	statusBar.setText("Trigger Set Failed. Hint : Textboxes must have valid numeric value.");
	    }
	}
}