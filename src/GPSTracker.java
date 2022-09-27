import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.omg.CORBA.NameValuePair;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class GPSTracker {

	private int localPortNo;
	private ServerSocket ss;

	private static ArrayList<GPSDevice> devices = new ArrayList();

	public GPSTracker(String localPort) throws Exception {
		this.localPortNo = Integer.parseInt(localPort);
		ss = new ServerSocket(localPortNo);

		System.out.println("TCP Server Created");
		logMessage("TCP Server Created", "Info");

	}

	public static void listenAndSend(int portNo) {
		HttpServer server;
		try {
			// server = HttpServer.create(new InetSocketAddress(portNo),
			// 0);192.168.1.10
			server = HttpServer.create(new InetSocketAddress(portNo), 0);
			server.createContext("/comando", new MyHandler());
			server.setExecutor(null); // creates a default executor
			server.start();

			System.out.println("HTTP Server Created");
			logMessage("HTTP Server Created", "Info");
		} catch (IOException e) {
			System.out.println("listenAndSend(int portNo) " + e.getMessage());
			logMessage("listenAndSend(int portNo) " + e.getMessage(), "Error");
		}

	}

	static class MyHandler implements HttpHandler {
		public void handle(HttpExchange t) throws IOException {
			try {

				if (t.getRequestMethod().equalsIgnoreCase("Get")) {

					String pair[] = t.getRequestURI().getQuery().split("=");
					if (pair.length == 2 && pair[0].equals("cmd")) {
						String command = "Send Command: " + pair[1];
						int i = 0;
						System.out.println("No of sockets we have "
								+ devices.size());
						logMessage("No of sockets we have " + devices.size(),
								"Info");

						while (i < devices.size()) {
							try {
								System.out.println("Device Id of Socket No "
										+ i
										+ " is "
										+ devices.get(i).deviceID
										+ " Socket "
										+ devices.get(i).socket
												.getRemoteSocketAddress());
								logMessage(
										"Device Id of Socket No "
												+ i
												+ " is "
												+ devices.get(i).deviceID
												+ " Socket "
												+ devices.get(i).socket
														.getRemoteSocketAddress(),
										"Info");
								if (pair[1].indexOf(devices.get(i).deviceID) != -1) {
									// Device is Found
									try {
										System.out
												.println("Writing to the device : "
														+ devices.get(i).deviceID);

										logMessage("Writing to the device : "
												+ devices.get(i).deviceID,
												"Info");

										devices.get(i).socket.getOutputStream()
												.write(pair[1].getBytes());
										System.out
												.println("Written successfully!");
										logMessage("Written successfully!",
												"Info");

									} catch (Exception e) {
										System.out
												.println("handle(HttpExchange t) "
														+ e.getMessage());
										logMessage("handle(HttpExchange t) "
												+ e.getMessage(), "Error");
										// Removes because it is failed
										devices.remove(i);
									}
									break;
								} else if (devices.get(i).deviceID
										.equals("Error")) {
									devices.get(i).socket.close();
									devices.remove(i);
									--i;
								}

							} catch (Exception e) {
								System.out.println("handle(HttpExchange t) "
										+ e.getMessage());
								logMessage(
										"handle(HttpExchange t) "
												+ e.getMessage(), "Error");
							}
							++i;
						}
						System.out.println(command);
						t.sendResponseHeaders(200, command.length());
						OutputStream os = t.getResponseBody();
						os.write(command.getBytes());
						os.close();
					}

				}
			} catch (Exception e) {
				System.out.println("handle(HttpExchange t) " + e.getMessage());
				logMessage("handle(HttpExchange t) " + e.getMessage(), "Error");

			}

		}
	}

	public static void sendGET(String targetURL) {

		try {

			// URL url = new URL(URLDecoder.decode(targetURL,
			// "UTF-8").replaceAll(" ", "%20"));
			URL url = new URL(targetURL);

			// URL url = new URL(URLEncoder.encode(targetURL, "UTF-8"));

			HttpURLConnection con = (HttpURLConnection) url.openConnection();

			// optional default is GET
			con.setRequestMethod("GET");

			// add request header
			// con.setRequestProperty("User-Agent", "");

			// int responseCode = con.getResponseCode();

			// System.out.println("\nSending 'GET' request to URL : "
			// + URLDecoder.decode(targetURL, "UTF-8"));
			// System.out.println("Response Code : " + responseCode);

			BufferedReader in = new BufferedReader(new InputStreamReader(
					con.getInputStream(), Charset.forName("UTF-8")));

			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();

			// print result
			System.out.println("Response value" + response.toString());
			logMessage("Response value" + response.toString(), "Info");

		} catch (Exception e) {
			System.out.println("Error " + e.getMessage());
			logMessage(e.getMessage(), "Error");

		}
	}

	public void lookForIncomingData() throws Exception {

		while (true) {
			Socket s = ss.accept();
			s.setSoTimeout(60000 * 2);
			System.out.println("Client Connected");
			logMessage("Client Connected", "Info");
			GPSDevice gDev = new GPSDevice();
			IncomingData id = new IncomingData(s, gDev);
			id.start();
			System.out.println("Waiting for client Data");
			logMessage("Waiting for client Data", "Info");

			// while (gDev.deviceID.equals(null) || gDev.socket == null) {
			// Waiting till the socket and device id is retrieved
			// }
			// System.out
			// .println("Socket and device id :"
			// + gDev.socket.getLocalSocketAddress() + " "
			// + gDev.deviceID);
			devices.add(gDev);

		}

	}

	public static void logMessage(String message, String type) {

		// Current Date and Time
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Calendar cal = Calendar.getInstance();

		try {
			String pathName = GPSTracker.class.getProtectionDomain()
					.getCodeSource().getLocation().getPath();
			pathName = pathName.substring(0, pathName.lastIndexOf("/"));

			File file = new File(pathName + "/Log");

			if (Files.notExists(file.toPath())) {
				try {
					Files.createDirectory(file.toPath());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					System.out.println(e.getLocalizedMessage());
				}
			}

			if (type.equalsIgnoreCase("Error")) {
				// System.out.println("inside error");

				PrintWriter writer = null;
				file = new File(pathName + "/Log/Error.txt");
				try {
					if (!file.exists())
						file.createNewFile();
					writer = new PrintWriter(new FileOutputStream(pathName
							+ "/Log/Error.txt", true));
					writer.println(dateFormat.format(cal.getTime()) + " : "
							+ message);
					// writer.println(message);
				} catch (IOException ex) {
					System.out
							.println("Error logMessage(String message,String type) : "
									+ ex.getMessage());
				} finally {
					if (writer != null) {
						writer.flush();
						writer.close();
					}
				}
			} /*
			 * else if (type.equalsIgnoreCase("Info")) { //
			 * System.out.println("inside info");
			 * 
			 * PrintWriter writer = null; file = new File(pathName+
			 * "/Log/Info.txt"); try { if (!file.exists()) file.createNewFile();
			 * writer = new PrintWriter(new FileOutputStream(pathName+
			 * "/Log/Info.txt", true)); // /var/www/gps.lokaliza.mx/socket
			 * writer.println(dateFormat.format(cal.getTime())+" : "+message);
			 * //writer.println(message); } catch (IOException ex) { System.out
			 * .println("Error logMessage(String message,String type) : " +
			 * ex.getMessage()); } finally { if (writer != null) {
			 * writer.flush(); writer.close(); } }
			 * 
			 * }
			 */
		} catch (Exception e) {
			System.out
					.println("Error logMessage(String message,String type) : "
							+ e.getMessage());
		}
	}

	public static void main(String[] arg) {

		try {
			System.out.print("Please Enter Port Number for TCP Listening : ");
			String tcpPort = new BufferedReader(
					new InputStreamReader(System.in)).readLine();

			System.out.print("Please Enter Port Number for Http listening : ");
			int httpPort = Integer.parseInt(new BufferedReader(
					new InputStreamReader(System.in)).readLine());
			GPSTracker c = new GPSTracker(tcpPort); // http listening
			GPSTracker.listenAndSend(httpPort); // tcp listening
			c.lookForIncomingData();

			// http://gps.lokaliza.mx?hdr=SA200STT&cell=1ef136&swver=299&lat=%2032.652169&lon=-115.465926&spd=000.031&devid=841296&crs=000.00&satt=9&fix=1&dist=67&pwrvolt=11.03&io=010010&mode=1&msgnum=0257&date=20150514&time=22:29:51&altId=0&emgId=0
			// http://gps.lokaliza.mx/?hdr=SA200STT&cell=1ef136&swver=299&lat=%2032.652169&lon=-115.465926&spd=000.031&devid=841296&crs=000.00&satt=9&fix=1&dist=67&pwrvolt=11.03&io=010010&mode=1&msgnum=0257&date=20150514&time=22:29:51&altId=0&emgId=0
			// GPSTracker.sendGET("http://gps.lokaliza.mx/?hdr=SA200STT&cell=1a801&swver=299&lat=19.455256&lon=-99.18862&spd=0.072&devid=841279&crs=0.0&satt=12&fix=1&dist=7604033&pwrvolt=12.86&io=001000&mode=1&msgnum=2005&date=20150526&time=18:37:17&altId=0&emgId=0&evt=0");

		} catch (Exception e) {
			System.out.println("main() " + e);
			logMessage("main() " + e.getMessage(), "Error");
		}

	}

}

class IncomingData extends Thread {
	private Socket soc = null;
	GPSDevice gDev;

	public IncomingData(Socket soc, GPSDevice gDev) throws Exception {
		this.soc = soc;
		this.gDev = gDev;
	}

	@Override
	public void run() {
		readIncomingData();
	}

	private void readIncomingData() {
		try {

			gDev.socket = soc;
			while (true) {

				String s = null;

				s = new BufferedReader(new InputStreamReader(
						soc.getInputStream())).readLine();

				System.out.println(s);
				GPSTracker.logMessage(s, "Info");
				try {

					StringTokenizer st = new StringTokenizer(s, ";");
					ArrayList<String> al = new ArrayList<String>();

					while (st.hasMoreTokens()) {
						al.add(st.nextToken());
					}

					if (al.size() > 15) {

						String hdr = al.get(0);
						String devid = "";
						int swver = 0;
						String date = "";
						String time = "";
						String cell = "";
						float lat = 0;
						float lon = 0;
						float spd = 0;
						float crs = 0;
						int satt = 0;
						int fix = 0;
						int dist = 0;
						float pwrvolt = 0;
						String io = "";

						int mode = 0;
						int msgnum = 0;
						int altId = 0;
						int emgId = 0;
						int evt = 0;

						if (hdr.equals("SA200STT")) {

							devid = al.get(1);
							swver = Integer.parseInt(al.get(2));
							date = al.get(3);
							time = al.get(4);
							cell = al.get(5);
							lat = Float.parseFloat(al.get(6));
							lon = Float.parseFloat(al.get(7));
							spd = Float.parseFloat(al.get(8));
							crs = Float.parseFloat(al.get(9));
							satt = Integer.parseInt(al.get(10));
							fix = Integer.parseInt(al.get(11));
							dist = Integer.parseInt(al.get(12));
							pwrvolt = Float.parseFloat(al.get(13));
							io = al.get(14);

							mode = Integer.parseInt(al.get(15));
							msgnum = Integer.parseInt(al.get(16));
						}

						else if (hdr.equals("SA200ALT")) {

							devid = al.get(1);
							swver = Integer.parseInt(al.get(2));
							date = al.get(3);
							time = al.get(4);
							cell = al.get(5);
							lat = Float.parseFloat(al.get(6));
							lon = Float.parseFloat(al.get(7));
							spd = Float.parseFloat(al.get(8));
							crs = Float.parseFloat(al.get(9));
							satt = Integer.parseInt(al.get(10));
							fix = Integer.parseInt(al.get(11));
							dist = Integer.parseInt(al.get(12));
							pwrvolt = Float.parseFloat(al.get(13));
							io = al.get(14);

							altId = Integer.parseInt(al.get(15));

						}

						else if (hdr.equals("SA200EMG")) {

							devid = al.get(1);
							swver = Integer.parseInt(al.get(2));
							date = al.get(3);
							time = al.get(4);
							cell = al.get(5);
							lat = Float.parseFloat(al.get(6));
							lon = Float.parseFloat(al.get(7));
							spd = Float.parseFloat(al.get(8));
							crs = Float.parseFloat(al.get(9));
							satt = Integer.parseInt(al.get(10));
							fix = Integer.parseInt(al.get(11));
							dist = Integer.parseInt(al.get(12));
							pwrvolt = Float.parseFloat(al.get(13));
							io = al.get(14);

							emgId = Integer.parseInt(al.get(15));
						}

						else if (hdr.equals("SA200CMD")) {

							devid = al.get(2);
							swver = Integer.parseInt(al.get(3));
							date = al.get(5);
							time = al.get(6);
							cell = al.get(7);
							lat = Float.parseFloat(al.get(8));
							lon = Float.parseFloat(al.get(9));
							spd = Float.parseFloat(al.get(10));
							crs = Float.parseFloat(al.get(11));
							satt = Integer.parseInt(al.get(12));
							fix = Integer.parseInt(al.get(13));
							dist = Integer.parseInt(al.get(14));
							pwrvolt = Float.parseFloat(al.get(15));
							io = al.get(16);

							mode = 0;
							msgnum = 0;
							altId = 0;
							emgId = 0;
							evt = 5;
						}

						else if (hdr.equals("SA200EVT")) {

							devid = al.get(1);
							swver = Integer.parseInt(al.get(2));
							date = al.get(3);
							time = al.get(4);
							cell = al.get(5);
							lat = Float.parseFloat(al.get(6));
							lon = Float.parseFloat(al.get(7));
							spd = Float.parseFloat(al.get(8));
							crs = Float.parseFloat(al.get(9));
							satt = Integer.parseInt(al.get(10));
							fix = Integer.parseInt(al.get(11));
							dist = Integer.parseInt(al.get(12));
							pwrvolt = Float.parseFloat(al.get(13));
							io = al.get(14);

							mode = 0;
							msgnum = 0;
							altId = 0;
							emgId = 0;
							evt = Integer.parseInt(al.get(15));
						} else if (hdr.equals("ST910")) {
							devid = al.get(2);
							swver = Integer.parseInt(al.get(3));
							date = al.get(4);
							time = al.get(5);
							cell = al.get(6);
							lat = Float.parseFloat(al.get(7));
							lon = Float.parseFloat(al.get(8));
							spd = Float.parseFloat(al.get(9));
							crs = Float.parseFloat(al.get(10));
							satt = Integer.parseInt(al.get(11));
							fix = Integer.parseInt(al.get(12));
							dist = Integer.parseInt(al.get(13));
							pwrvolt = Float.parseFloat(al.get(14));
							io = al.get(15);

							mode = Integer.parseInt(al.get(16));
							msgnum = Integer.parseInt(al.get(17));
						}

						gDev.deviceID = devid + "";

						String param = "hdr=" + hdr + "&cell=" + cell
								+ "&swver=" + swver + "&lat=" + lat + "&lon="
								+ lon + "&spd=" + spd + "&devid=" + devid
								+ "&crs=" + crs + "&satt=" + satt + "&fix="
								+ fix + "&dist=" + dist + "&pwrvolt=" + pwrvolt
								+ "&io=" + io + "&mode=" + mode + "&msgnum="
								+ msgnum + "&date=" + date + "&time=" + time
								+ "&altId=" + altId + "&emgId=" + emgId
								+ "&evt=" + evt;

						String sUrl = "http://gps.lokaliza.mx/?" + param;
						// System.out.println(sUrl);

						GPSTracker.sendGET(sUrl);

					} else {
						// gDev.deviceID = "Error";
					}
				} catch (Exception e) {
					System.out.println("readIncomingData() " + e.getMessage());
					// GPSTracker.logMessage(
					// "readIncomingData() " + e.getMessage(), "Error");
					gDev.deviceID = "Error";
					try {
						soc.close();
					} catch (IOException e1) {

					}

					break;
				}
			}
		} catch (Exception e) {
			System.out.println("readIncomingData() " + e.getMessage());
			// GPSTracker.logMessage("readIncomingData() " + e.getMessage(),
			// "Error");
		} finally {
			try {
				soc.close();
			} catch (IOException e) {
				System.out.println(e.getMessage());
				// GPSTracker.logMessage(
				// "readIncomingData() " + e.getMessage(), "Error");
				GPSTracker.logMessage("readIncomingData() Socket Closing",
						"Info");
				gDev.deviceID = "Error";
				try {
					soc.close();
				} catch (IOException e1) {
					// GPSTracker.logMessage(
					// "readIncomingData() " + e1.getMessage(), "Error");
				}
			}
		}
	}

}
