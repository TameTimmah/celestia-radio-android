package com.landonmanning.mediaplayer.celestiaradio;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Celestia Radio Main Activity
 * @author Landon "Karai" Manning
 * @email LManning17@gmail.com
 *
 */
public class MainActivity extends Activity {
    private MediaPlayer player;						// Media player
    private MetaTask metaTask;						// Async Task for continuous updating
    private ImageView logo;							// Company logo
    private TextView artist, title, serverTitle;	// Artist & Title data
    private ImageButton togglePlay;					// Play/Stop button
	
	/**
	 * Create Activity
	 */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        //-- System Stuff --
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.main);
        
        //-- Prepare Variables --
        this.player			= new MediaPlayer();
        this.metaTask		= new MetaTask();
        this.logo			= (ImageView) findViewById(R.id.logo);
        this.artist			= (TextView) findViewById(R.id.artist);
        this.title			= (TextView) findViewById(R.id.title);
        this.serverTitle	= (TextView) findViewById(R.id.serverTitle);
        this.togglePlay		= (ImageButton) findViewById(R.id.togglePlay);
        
        //-- Make Links Clickable --
        this.artist.setMovementMethod(LinkMovementMethod.getInstance());
        this.title.setMovementMethod(LinkMovementMethod.getInstance());
        
        //-- Enable Marquee Effect --
    	this.artist.setSelected(true);
    	this.title.setSelected(true);
    	
    	//-- Prepare Meta Task --
    	this.metaTask.execute(getString(R.string.stats));
        
    	//-- Prepare MediaPlayer --
        this.player.setOnPreparedListener(new OnPreparedListener() {
			public void onPrepared(MediaPlayer mp) {
				mp.start();
				MainActivity.this.togglePlay.setBackgroundDrawable(getResources().getDrawable(R.drawable.stop));
			}
    	});
        
        //-- Make Logo Link to Website --
        this.logo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse(getString(R.string.website)));
                startActivity(intent);
            }
        });
    	
    	//-- Toggle Play Button --
        this.togglePlay.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				togglePlay();
			}
		});
    }
	
    /**
     * Change Configuration
     */
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
	
	/**
	 * Pause Activity
	 */
	@Override
	protected void onPause() {
		super.onPause();
		
		if(isFinishing()) {
			this.metaTask.stop();
			this.player.release();
		}
	}
	
	/**
	 * Toggle Play Button
	 */
	private void togglePlay() {
		try {
			if (!this.player.isPlaying()) {
		    	this.player.setAudioStreamType(AudioManager.STREAM_MUSIC);
				this.player.setDataSource(getString(R.string.address));
				this.player.prepareAsync();
			} else {
				this.player.reset();
				this.togglePlay.setBackgroundDrawable(getResources().getDrawable(R.drawable.play));
			}
		} catch (IllegalArgumentException e) {
			Log.e("ERROR: togglePlay", "Invalid data source!");
			e.printStackTrace();
		} catch (IllegalStateException e) {
			Log.e("ERROR: togglePlay", "Play state buggered up!");
			e.printStackTrace();
		} catch (IOException e) {
			Log.e("ERROR: togglePlay", "Invalid data source!");
			e.printStackTrace();
		}
	}
	
	/**
	 * Set Meta Data
	 * @param json		JSON object from stats page
	 */
	private void setMeta(JSONObject json) {
		try {
			//-- Parse Top Level JSON --
			String currentListeners		= json.getString("CURRENTLISTENERS");
			String serverTitle			= json.getString("SERVERTITLE");
			JSONArray songHistoryArray	= json.getJSONArray("SONGHISTORY");
			String songHistory[][];
			
			songHistory = new String[10][5];
			
			//-- Parse SONGHISTORY --
			for (int i = 0; i < songHistoryArray.length(); i++) {
				JSONObject sh = songHistoryArray.getJSONObject(i);
				String h[] = {
						sh.getString("PLAYEDAT"),
						sh.getString("ARTISTID"),
						sh.getString("ARTIST"),
						sh.getString("SONGID"),
						sh.getString("SONG"),
						sh.getString("TITLE"),
				};
				
				if (!sh.isNull("ARTISTID")) {
					h[1] = "<a href='http://eqbeats.org/user/" + h[1] + "'>";
					h[2] = h[2] + "</a>";
				} else {
					h[1] = "";
				}
				
				if (!sh.isNull("SONGID")) {
					h[3] = "<a href='http://eqbeats.org/track/" + h[3] + "'>";
					h[4] = h[4] + "</a>";
				} else {
					h[3] = "";
				}
				
				songHistory[i] = h;
			}
			
			//-- Set Meta Data --
			this.serverTitle.setText(currentListeners + " ponies tuned in to " + serverTitle + "!");
			this.artist.setText(Html.fromHtml(songHistory[0][1] + songHistory[0][2]));
			this.title.setText(Html.fromHtml(songHistory[0][3] + songHistory[0][4]));
		} catch (JSONException e) {
			Log.e("ERROR: setMeta", "Error parsing JSON!");
			e.printStackTrace();
		}
	}
	
	/**
	 * Get Meta Data From SHOUTcast Stats page (http://ponify.me/stats.php)
	 * @author Landon Manning
	 * @email LManning17@gmail.com
	 *
	 */
	private class MetaTask extends AsyncTask<String, String, String> {
		private Boolean running = true;
		
		public void stop() {
			this.running = false;
		}
		
		protected String doInBackground(String... address) {
			while(this.running) {
				try {
	  	       		//-- Connect to and read Meta Data page --
	  	       		URLConnection con = new URL(address[0]).openConnection();
	  	       		Reader r = new InputStreamReader(con.getInputStream());
	  	       		
	  	       		//-- Build JSON String --
	  	       		StringBuilder buffer = new StringBuilder();
	  	       		int ch;
	  	       		
	  	       		while (true) {
	  	       			ch = r.read();
	  	       		
	  	       			if (ch < 0)
	  	       				break;
	  	       		
	  	       			buffer.append((char) ch);
	  	       		}
	  	       		
	  	       		this.publishProgress(buffer.toString());
	  	       		Thread.sleep(30000); // 30 seconds
	  			} catch (MalformedURLException e) {
	  				Log.e("ERROR: doInBackground", "Invalid URL!");
	  				e.printStackTrace();
	  			} catch (IOException e) {
	  				Log.e("ERROR: doInBackground", "Error reading input stream!");
	  				e.printStackTrace();
	  			} catch (InterruptedException e) {
	  				Log.e("ERROR: doInBackground", "Thread sleep interrupted!");
	  				e.printStackTrace();
				}
			}
			
			return null;
		}
		
		protected void onProgressUpdate(String... values) {
			if (!this.running)
				return;
			
			//-- Send JSONObject --
			try {
				JSONObject json = new JSONObject(values[0].toString());
				MainActivity.this.setMeta(json);
			} catch (JSONException e) {
  				Log.e("ERROR: onProgressUpdate", "Error parsing JSON!");
				e.printStackTrace();
			}
		}
	}
}