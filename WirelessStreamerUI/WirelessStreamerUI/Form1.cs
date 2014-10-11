using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Windows.Forms;
using NAudio;
using NAudio.Lame;
using System.Diagnostics;
using NAudio.Wave;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;


namespace WirelessStreamerUI
{
    public partial class Form1 : Form
    {
        
        static LameMP3FileWriter wri_rec;
        int time_counter = 0;
        public int sound_quality = 32;
        IWaveIn waveIn_rec;
        string file_path = @"C:\temp\recorded_audio.mp3";
        string file_name = "Sample";
        string status_msg;
        static public int send_sound_quality= 32;
        static bool stopped;
        IPAddress ipaddr;
        TcpListener serverSocket;
        TcpClient clientSocket;
        int counter, pc;

        public Form1()
        {
            InitializeComponent();
            stop_rec.Enabled = false;
            label2.Visible = true;
            label4.Text = GetIP();
        }

        public string GetIP()
        {
            string strHostName = "";
            string strIPaddr1 = "";
            string strIPaddr2 = "";
            string strIPaddr3 = "";
            string strIPaddr = "";
            strHostName = Dns.GetHostName();
            //Console.WriteLine("Local Machine's Host Name: " + strHostName);
            IPHostEntry ipEntry = Dns.GetHostEntry(strHostName);
            IPAddress[] addr = ipEntry.AddressList;
            for (int i = 0; i < addr.Length; i++)
                strIPaddr1 = addr[i].ToString();

            strIPaddr2 = addr[1].ToString();
           strIPaddr3 = addr[2].ToString();
            Debug.Write("\n" + strIPaddr1 + strIPaddr1.Length);
            Debug.Write("\n" + strIPaddr2 +" "+ strIPaddr2.Length);

            if (strIPaddr1.Length <= 15)
                strIPaddr = strIPaddr1;
            else if (strIPaddr2.Length <= 15)
                strIPaddr = strIPaddr2;
            else if (strIPaddr3.Length <= 15)
                strIPaddr = strIPaddr3;
            
            return strIPaddr;
        }

        // actions to be performed when the "Server start" button is clicked
        private void connect_btn_Click(object sender, EventArgs e)
        {
            groupBox2.Enabled = false;
            startserver_btn.Enabled = false;
            stop_server_btn.Enabled = true;
            // start backgroundWorker1 if it is not busy
            if (!backgroundWorker1.IsBusy)
            {
                backgroundWorker1.RunWorkerAsync();
            }
            if (serverSocket != null) serverSocket.Stop();
        }

        // actions to be performed when the stop server is clicked
        private void stop_server_btn_Click(object sender, EventArgs e)
        {
            if (backgroundWorker1.IsBusy)
            {
                try
                {
                    // stop the backgroundWorker1
                    backgroundWorker1.CancelAsync();
                    // stop the server socket
                    serverSocket.Stop();
                    if (clientSocket != null)
                        clientSocket.Close();
                }
                catch (Exception exp23){
                    Debug.Write(exp23.Message);
                }
            }
            // Display message on textBox
            textBox1.Text = "Disconnected " + Environment.NewLine + "Click on start server" + Environment.NewLine;
            startserver_btn.Enabled = true;
            stop_server_btn.Enabled = false;
            groupBox2.Enabled = true;
        }

        private void backgroundWorker1_DoWork(object sender, DoWorkEventArgs e)
        {
            ipaddr = IPAddress.Parse(GetIP());
            //
            serverSocket = new TcpListener(ipaddr, 13000);
            clientSocket = default(TcpClient);
            counter = 0;
            // Starting server
            serverSocket.Start();
            backgroundWorker1.ReportProgress(22);
            Debug.Write("Server started");
            counter = 0;

            while (true)
            {
                // enter into an infinite while loop to listen to multiple clients
                counter += 1;
                clientSocket = serverSocket.AcceptTcpClient();
                clientSocket.NoDelay = true;
                pc = counter;
                backgroundWorker1.ReportProgress(counter);
                // once the connection is accepted, create a new client object
                handleClinet client = new handleClinet();
                //  call the startClient
                client.startClient(clientSocket, Convert.ToString(counter));
            }
            serverSocket.Stop();
           
        }

        private void backgroundWorker1_ProgressChanged(object sender, ProgressChangedEventArgs e)
        {
            if (e.ProgressPercentage == 22)
            {
                textBox1.Text += "Server started" + Environment.NewLine;
            }
            if (e.ProgressPercentage == pc)
            {
                textBox1.Text += "Client " + pc + " connected" + Environment.NewLine;
            }

        }

        private void backgroundWorker1_RunWorkerCompleted(object sender, RunWorkerCompletedEventArgs e)
        {
            if (e.Cancelled)
            {
                clientSocket.Close();
                serverSocket.Stop();
            }
            else if (e.Error != null)
            {
                Debug.Write(e.Error.Message);
                clientSocket.Close();
                serverSocket.Stop();
            }

        }

        public class handleClinet
        {
            // Networkstream required for network oeprations
            NetworkStream stream = null;
            MemoryStream memStream;
            TcpClient clientSocket;
            string clNo;
            static bool stopped = false;
            Byte[] bytes = new Byte[20];
            byte[] SoundBytes = new byte[10000];
            LameMP3FileWriter wri;


            public void startClient(TcpClient inClientSocket, string clineNo)
            {
                this.clientSocket = inClientSocket;
                this.clNo = clineNo;
                // Start new thread for each client and invoke streamSound method
                Thread ctThread = new Thread(streamSound);
                ctThread.Start();
            }

            private void streamSound()
            {
                stream = clientSocket.GetStream();
                try
                {
                    int i = 0;
                    // if the length of the bytes read is greater than zero, continue for loop
                    while ((i = stream.Read(bytes, 0, bytes.Length)) != 0)
                    {
                        // create a new waveIn object of type WasapiLoopbackCapture()
                        IWaveIn waveIn = new WasapiLoopbackCapture();
                        // set waveIn.DataAvailable event to waveIn_DataAvailable() defined later
                        waveIn.DataAvailable += waveIn_DataAvailable;
                        // set waveIn.RecordingStopped event to waveIn_RecordingStopped() defined later
                        waveIn.RecordingStopped += waveIn_RecordingStopped;
                        memStream = new MemoryStream();
                        // set memoryStream as the location to store the captured audio
                        wri = new LameMP3FileWriter(memStream, waveIn.WaveFormat, send_sound_quality);
                        // Start recording
                        waveIn.StartRecording();
                        // sleep for 900 * 2 milliseconds
                        Thread.Sleep(900);

                        Thread.Sleep(900);
                        // stop recording
                        waveIn.StopRecording();
                        // flush the buffer contents to memStream
                        wri.Flush();
                        //Convert memStream to array and store it onto SoundBytes byte[]
                        SoundBytes = memStream.ToArray();
                        // write the byte[] to network stream
                        stream.Write(SoundBytes, 0, SoundBytes.Length);
                        // flush the stream
                        stream.Flush();
                        wri.Dispose();
                        waveIn.Dispose();
                        Array.Clear(SoundBytes, 0, SoundBytes.Length);
                    }
                    if (Thread.CurrentThread.IsAlive)
                    {
                        Thread.CurrentThread.Abort();
                    }

                }
                catch (NullReferenceException exp)
                {
                    Debug.Write(exp.Message);
                }
                catch (Exception exp1)
                {
                    Debug.Write(exp1.Message);
                }
            }

            void waveIn_RecordingStopped(object sender, StoppedEventArgs e)
            {
                stopped = true;
            }

            void waveIn_DataAvailable(object sender, WaveInEventArgs e)
            {
                // write recorded data to MP3 writer
                if (this.wri != null)
                {
                    wri.Write(e.Buffer, 0, e.BytesRecorded);
                }
            }

        }

        private void radioButton4_CheckedChanged(object sender, EventArgs e)
        {
            send_sound_quality = 16;
        }

        private void radioButton5_CheckedChanged(object sender, EventArgs e)
        {
            send_sound_quality = 32;
        }

        private void radioButton6_CheckedChanged(object sender, EventArgs e)
        {
            send_sound_quality = 64;
        }


        // functions required for recording onto a file

        private void start_rec_Click(object sender, EventArgs e)
        {
            waveIn_rec = new WasapiLoopbackCapture();
            // Start recording from loopback
            waveIn_rec.DataAvailable += waveIn_DataAvailable;
            waveIn_rec.RecordingStopped += waveIn_RecordingStopped;
            wri_rec = new LameMP3FileWriter(file_path, waveIn_rec.WaveFormat, sound_quality);
            label2.Text = Convert.ToString("Recording at: " + sound_quality + " bits/sec");
            path_text_box.Text = file_path;
            waveIn_rec.StartRecording();
            path_text_box.Enabled = false;
            start_rec.Enabled = false;
            stop_rec.Enabled = true;
            groupBox1.Enabled = false;
            label2.Visible = true;
            timer1.Start();
            browse_btn.Enabled = false;
        }

        private void stop_rec_Click(object sender, EventArgs e)
        {
            start_rec.Enabled = true;
            stop_rec.Enabled = false;
            groupBox1.Enabled = true;
            browse_btn.Enabled = true;
            label2.Visible = false;
            path_text_box.Enabled = true;
            label2.Text = "Click on 'Start Recording' to begin recording!";
            waveIn_rec.StopRecording();
            // stop timer and reset time_counter
            timer1.Stop();
            timer_label.Text = Convert.ToString("Total recording time: " + time_counter / 10);
            time_counter = 0;
            // flush output to finish MP3 file correctly
            wri_rec.Flush();
            // Dispose of objects
            waveIn_rec.Dispose();
            wri_rec.Dispose();
        }

        static void waveIn_RecordingStopped(object sender, StoppedEventArgs e)
        {
            // signal that recording has finished
             stopped = true;
        }

        static void waveIn_DataAvailable(object sender, WaveInEventArgs e)
        {
            // write recorded data to MP3 writer
            if (wri_rec != null)
                wri_rec.Write(e.Buffer, 0, e.BytesRecorded);
        }

        private void radioButton1_CheckedChanged(object sender, EventArgs e)
        {
            sound_quality = 32;
        }

        private void radioButton2_CheckedChanged(object sender, EventArgs e)
        {
            sound_quality = 64;
        }

        private void radioButton3_CheckedChanged(object sender, EventArgs e)
        {
            sound_quality = 128;
        }

        private void browse_btn_Click(object sender, EventArgs e)
        {
            FolderBrowserDialog dlg = new FolderBrowserDialog();
            if (dlg.ShowDialog() == DialogResult.OK)
            {
                file_path = dlg.SelectedPath;
                file_path += "\\" + file_name + ".mp3";
                path_text_box.Text = file_path;
            }
        }

        private void timer1_Tick(object sender, EventArgs e)
        {
            time_counter++;
            timer_label.Text = Convert.ToString(time_counter);
        }

        private void path_text_box_TextChanged(object sender, EventArgs e)
        {
            file_path = "";
            file_path = path_text_box.Text;
        }
    }
}
