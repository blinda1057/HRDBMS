package com.exascale.threads;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.StringTokenizer;

import com.exascale.managers.HRDBMSWorker;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

public class StartCoordsThread extends HRDBMSThread
{	
	public StartCoordsThread()
	{
		this.setWait(true);
		this.description = "Start Coordinators";
	}
	
	public void run()
	{
		try
		{
			BufferedReader in = new BufferedReader(new FileReader(new File("nodes.cfg")));
			String line = in.readLine();
			while (line != null)
			{
				StringTokenizer tokens = new StringTokenizer(line, ",", false);
				String host = tokens.nextToken().trim();
				String type = tokens.nextToken().trim().toUpperCase();
				if (type.equals("C") || type.equals("W"))
				{
				}
				else
				{
					HRDBMSWorker.logger.error("Type found in nodes.cfg was not valid: " + type);
					System.exit(1);
				}
				
				if (type.equals("C"))
				{
					InetAddress addr = InetAddress.getByName(host);
					if (isThisMyIpAddress(addr))
					{
						line = in.readLine();
						continue;
					}
					
					String user = HRDBMSWorker.getHParms().getProperty("hrdbms_user");
					HRDBMSWorker.logger.info("Starting coordinator " + host);
					String command1 = "java -Xmx4096m -cp HRDBMS.jar:jsch-0.1.50.jar:log4j-api-2.0-beta8.jar:log4j-core-2.0-beta8.jar:. com.exascale.managers.HRDBMSWorker " + HRDBMSWorker.TYPE_COORD;
			        try{
			             
			            java.util.Properties config = new java.util.Properties(); 
			            config.put("StrictHostKeyChecking", "no");
			            JSch jsch = new JSch();
			            Session session=jsch.getSession(user, host, 22);
			            UserInfo ui = new MyUserInfo();
			            session.setUserInfo(ui);
			            jsch.addIdentity(".ssh/id_dsa");
			            session.setConfig(config);
			            session.connect();
			             
			            Channel channel=session.openChannel("exec");
			            ((ChannelExec)channel).setCommand(command1);
			            channel.setInputStream(null);
			            ((ChannelExec)channel).setErrStream(System.out);
			            ((ChannelExec)channel).setOutputStream(System.out);
			             
			            InputStream in2 = channel.getInputStream();
			            channel.connect();
			            byte[] tmp=new byte[1024];
			            while(in2.available() > 0)
			            {
			                in2.read(tmp, 0, 1024);
			            }
			            channel.disconnect();
			            session.disconnect();
			        }catch(Exception e){
			        	HRDBMSWorker.logger.error("Failed to start a coordinator node.", e);
			            System.exit(1);
			        }
				}
				
				line = in.readLine();
			}
			HRDBMSWorker.logger.debug("Start Coordinator is about to terminate.");
			this.terminate();
			return;
		}
		catch(Exception e)
		{
			HRDBMSWorker.logger.error("Failed to start a coordinator node.", e);
            System.exit(1);
		}
	}
	
	public static boolean isThisMyIpAddress(InetAddress addr) {
	    // Check if the address is a valid special local or loop back
	    if (addr.isAnyLocalAddress() || addr.isLoopbackAddress())
	        return true;

	    // Check if the address is defined on any interface
	    try {
	        return NetworkInterface.getByInetAddress(addr) != null;
	    } catch (SocketException e) {
	        return false;
	    }
	}
	
	private class MyUserInfo implements UserInfo
	{

		@Override
		public String getPassphrase() {
			return "";
		}

		@Override
		public String getPassword() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean promptPassphrase(String arg0) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean promptPassword(String arg0) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean promptYesNo(String arg0) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void showMessage(String arg0) {
			// TODO Auto-generated method stub
			
		}
		
	}
}