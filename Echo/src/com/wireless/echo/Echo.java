package com.wireless.echo;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class Echo extends Activity {
	private static final int SERVER_PORT = 13000;
	private static final int BUFFER_SIZE = 8000;
	private static final String SHARED_PREFERENCE_FILE = "MostRecentip";
	private Socket socket = null;
	private Button btnConnect;
	private Button btnDisconnect;
	private TextView tvStatus;
	private LinearLayout layoutStatus;
	private DataOutputStream outStream;
	private DataInputStream inStream;
	private Thread socketThread = null;
	private Handler handler = new Handler();
	private ProgressBar progressBar;
	private MediaPlayer mediaPlayer = new MediaPlayer();
	private EditText etIp1;
	private EditText etIp2;
	private EditText etIp3;
	private EditText etIp4;
	private String serverIp;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.echo);

		btnConnect = (Button) findViewById(R.id.btnConnect);
		btnConnect.setOnClickListener(buttonListener);
		btnDisconnect = (Button) findViewById(R.id.btnDisconnect);
		btnDisconnect.setOnClickListener(buttonListener);
		layoutStatus = (LinearLayout) findViewById(R.id.llStatus);
		tvStatus = (TextView) findViewById(R.id.tvEnterServerIp);
		etIp1 = (EditText) findViewById(R.id.etIpPart1);
		etIp2 = (EditText) findViewById(R.id.etIpPart2);
		etIp3 = (EditText) findViewById(R.id.etIpPart3);
		etIp4 = (EditText) findViewById(R.id.etIpPart4);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		setAutoFocusEditText();
	}

	/**
	 * Handles the configuration changes. Always used portrait mode.
	 */
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	}

	/**
	 * Focuses the next edit text while entering the IP address of the server.
	 */
	private void setAutoFocusEditText() {
		etIp1.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (s.length() == 3) {
					etIp2.requestFocus();
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		etIp2.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (s.length() == 3) {
					etIp3.requestFocus();
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		etIp3.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (s.length() == 3) {
					etIp4.requestFocus();
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
	}

	/**
	 * Handles the button click events. Starts a new thread to perform network
	 * operations and play the audio.
	 */
	private OnClickListener buttonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.btnConnect:

				progressBar.setVisibility(View.VISIBLE);

				try {
					int ip1 = Integer.parseInt(etIp1.getText().toString());
					int ip2 = Integer.parseInt(etIp2.getText().toString());
					int ip3 = Integer.parseInt(etIp3.getText().toString());
					int ip4 = Integer.parseInt(etIp4.getText().toString());

					if (ip1 <= 255 && ip2 <= 255 && ip3 <= 255 && ip4 <= 255) {
						// form server IP address from the edit text after
						// validation
						serverIp = ip1 + "." + ip2 + "." + ip3 + "." + ip4;
						socketThread = new Thread(new Runnable() {

							@Override
							public void run() {
								connect();
							}
						});
						socketThread.start();

						setMostRecentIps();
					} else {
						Toast.makeText(getApplicationContext(),
								"Invalid IP address", Toast.LENGTH_SHORT)
								.show();
						progressBar.setVisibility(View.GONE);
					}
				} catch (NumberFormatException e) {
					// invalid IP address
					Toast.makeText(getApplicationContext(),
							"Invalid IP address", Toast.LENGTH_SHORT).show();
					progressBar.setVisibility(View.GONE);
				}
				break;

			case R.id.btnDisconnect:
				disconnect();
				if (socketThread != null) {
					// terminate the background thread
					socketThread.interrupt();
				}
				break;
			default:
				break;
			}
		}

		/**
		 * Sets 3 most recently used IP addresses of the server using the Shared
		 * Preferences.
		 */
		private void setMostRecentIps() {
			SharedPreferences spMostRecentIps = getSharedPreferences(
					SHARED_PREFERENCE_FILE, MODE_PRIVATE);
			int count = Integer.parseInt(spMostRecentIps
					.getString("count", "0"));
			Set<Entry<String, String>> allIps = getMostRecentIps().entrySet();
			boolean alreadyExists = false;
			for (Entry<String, String> ip : allIps) {
				if (ip.getValue().equals(serverIp)) {
					alreadyExists = true;
					break;
				}
			}
			if (!alreadyExists) {
				// add the IP address, if it is not in existing list
				String lruIp = "ip" + ((count % 3) + 1);
				SharedPreferences.Editor editor = spMostRecentIps.edit();
				editor.putString(lruIp, serverIp);
				editor.putString("count", "" + ((count + 1) % 3));
				editor.commit();
			}
		}
	};

	/**
	 * The function connects, communicates with the server and plays the audio
	 * being streamed from the server. Exceptions are thrown during invalid
	 * behavior.
	 */
	private void connect() {

		try {
			socket = new Socket();
			socket.setTcpNoDelay(true);
			socket.setTrafficClass(24);
			// connect to the server with 5 seconds timeout
			socket.connect(
					new InetSocketAddress(InetAddress.getByName(serverIp),
							SERVER_PORT), 5000);
			if (socket != null) {
				handler.post(new Runnable() {
					// communicate with UI thread
					@Override
					public void run() {
						btnDisconnect.setVisibility(View.VISIBLE);
						btnConnect.setVisibility(View.INVISIBLE);
						tvStatus.setText("Connected to server");
						layoutStatus.setBackgroundColor(getResources()
								.getColor(R.color.lightGreen));
						etIp1.setEnabled(false);
						etIp2.setEnabled(false);
						etIp3.setEnabled(false);
						etIp4.setEnabled(false);
						progressBar.setVisibility(View.GONE);
					}
				});
				// create the input and output streams
				outStream = new DataOutputStream(socket.getOutputStream());
				inStream = new DataInputStream(socket.getInputStream());

				int bytesRead = 0;
				byte[] mp3dataIn = new byte[BUFFER_SIZE];

				outStream.write(new String("Start recording").getBytes());

				String fileName = getCacheDir().getPath() + "/temp.mp3";
				ByteArrayOutputStream mp3ByteStream;
				byte[] mp3ByteArray;

				// read the data from the server continuously and play the audio
				while ((bytesRead = inStream.read(mp3dataIn)) > 1) {
					outStream
							.write(new String("Continue recording").getBytes());

					if (mediaPlayer != null) {
						mediaPlayer.release();
					}

					mp3ByteStream = new ByteArrayOutputStream();
					mp3ByteArray = new byte[BUFFER_SIZE];
					mp3ByteStream.write(mp3dataIn, 0, bytesRead);
					mp3ByteArray = mp3ByteStream.toByteArray();

					// create mp3 and play
					File tempMp3 = new File(fileName);
					tempMp3.deleteOnExit();
					FileOutputStream fos = new FileOutputStream(tempMp3);
					fos.write(mp3ByteArray);
					fos.close();

					mediaPlayer = MediaPlayer.create(Echo.this,
							Uri.parse(fileName));

					try {
						mediaPlayer.start();
					} catch (Exception e) {
						// Media reception error, skip frame
					}
				}
			} else {
				handler.post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getApplicationContext(),
								"Connection failed", Toast.LENGTH_SHORT).show();
					}
				});
			}
		} catch (UnknownHostException e) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(getApplicationContext(),
							"Unable to connect to server. Verify server IP",
							Toast.LENGTH_SHORT).show();
					progressBar.setVisibility(View.GONE);
				}
			});
		} catch (NoRouteToHostException e) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(getApplicationContext(),
							"Unable to connect to server. Verify server IP",
							Toast.LENGTH_SHORT).show();
					progressBar.setVisibility(View.GONE);
				}
			});
		} catch (IOException e) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(getApplicationContext(),
							"Connection failed", Toast.LENGTH_SHORT).show();
					progressBar.setVisibility(View.GONE);
				}
			});
		} catch (Exception e) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(getApplicationContext(),
							"Connection failed", Toast.LENGTH_SHORT).show();
					progressBar.setVisibility(View.GONE);
				}
			});
		}
	}

	/**
	 * Handles the disconnect operation, closes all the open resources.
	 */
	private void disconnect() {
		try {
			if (mediaPlayer != null) {
				mediaPlayer.release();
			}
			if (outStream != null) {
				outStream.write(new String("").getBytes());
				outStream.close();
			}
			if (socket != null) {
				socket.close();
			}
			btnConnect.setVisibility(View.VISIBLE);
			btnDisconnect.setVisibility(View.INVISIBLE);
			layoutStatus.setBackgroundColor(getResources().getColor(
					R.color.lightRed));
			etIp1.setEnabled(true);
			etIp2.setEnabled(true);
			etIp3.setEnabled(true);
			etIp4.setEnabled(true);
			progressBar.setVisibility(View.GONE);
		} catch (IOException e) {
			// socket exception to close
		}
	}

	/**
	 * Create the options menu.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.echo, menu);
		return true;
	}

	/**
	 * Handles the menu option selection
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.most_recent:
			showMostRecentIPs();
			break;
		case R.id.help:
			showHelp();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Display the most recent IP addresses in a dialog.
	 */
	private void showMostRecentIPs() {
		AlertDialog.Builder builder = new AlertDialog.Builder(Echo.this);
		final CharSequence[] mostRecentIps = getMostRecentIps().values()
				.toArray(new CharSequence[3]);

		builder.setItems(mostRecentIps, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				setIpAddress(mostRecentIps[item].toString());
			}
		}).setTitle(R.string.most_recent);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	// @Override
	// protected void onPause() {
	// super.onPause();
	// disconnect();
	// }

	/**
	 * Handle destroy event. Close all the open resources by calling disconnect
	 * function.
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		disconnect();
	}

	/**
	 * Get the most recent IP addresses used from the Shared Preferences.
	 */
	private Map<String, String> getMostRecentIps() {
		Map<String, String> mostRecentIps = new HashMap<String, String>();
		SharedPreferences mostRecentIp = getSharedPreferences(
				SHARED_PREFERENCE_FILE, MODE_PRIVATE);
		String ip1 = mostRecentIp.getString("ip1", "None");
		String ip2 = mostRecentIp.getString("ip2", "None");
		String ip3 = mostRecentIp.getString("ip3", "None");
		mostRecentIps.put("1", ip1);
		mostRecentIps.put("2", ip2);
		mostRecentIps.put("3", ip3);
		return mostRecentIps;
	}

	/**
	 * Sets the IP address selected to the edit text fields.
	 */
	private void setIpAddress(String ipAddress) {
		if (ipAddress.equals("None")) {
			// do nothing, if it is not an IP address
			return;
		}
		String[] ipAddressChunks = ipAddress.split("[.]");
		etIp1.setText(ipAddressChunks[0]);
		etIp2.setText(ipAddressChunks[1]);
		etIp3.setText(ipAddressChunks[2]);
		etIp4.setText(ipAddressChunks[3]);
	}

	/**
	 * Display the help dialog.
	 */
	private void showHelp() {
		AlertDialog.Builder builder = new AlertDialog.Builder(Echo.this);
		LayoutInflater inflater = Echo.this.getLayoutInflater();
		builder.setView(inflater.inflate(R.layout.help, null))
				.setTitle(R.string.help_title)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
					}
				});
		AlertDialog dialog = builder.create();
		dialog.show();
	}
}
