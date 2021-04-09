package com.cooper.wheellog.utils;

import com.cooper.wheellog.WheelData;
import com.cooper.wheellog.WheelLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import timber.log.Timber;

public class InmotionAdapterV2 extends BaseAdapter {
    private static InmotionAdapterV2 INSTANCE;
    private Timer keepAliveTimer;
    private boolean settingCommandReady = false;
    private boolean requestSettings = false;
    private boolean turningOff = false;
    private static int updateStep = 0;
    private static int stateCon = 0;
    private byte[] settingCommand;
    InmotionUnpackerV2 unpacker = new InmotionUnpackerV2();

    @Override
    public boolean decode(byte[] data) {
        for (byte c : data) {
            if (unpacker.addChar(c)) {

                Message result = Message.verify(unpacker.getBuffer());

                if (result != null) {
                    Timber.i("Get new data, command: %02X", result.command);
                    if (result.flags == Message.Flag.Initial.getValue()) {
                        if (result.command == Message.Command.MainInfo.getValue()) {
                            return result.parseMainData();
                        } else if ((result.command == Message.Command.Diagnistic.getValue()) && turningOff) {
                            settingCommand = InmotionAdapterV2.Message.wheelOffSecondStage().writeBuffer();
                            turningOff = false;
                            settingCommandReady = true;
                            return false;
                        }
                    } else if (result.flags == Message.Flag.Default.getValue()) {
                        if (result.command == Message.Command.Settings.getValue()) {
                            requestSettings = false;
                            return result.parseSettings();
                        } else if (result.command == Message.Command.Diagnistic.getValue()) {
                            return result.parseDiagnostic();
                        } else if (result.command == Message.Command.BatteryRealTimeInfo.getValue()) {
                            return result.parseBatteryRealTimeInfo();
                        } else if (result.command == Message.Command.TotalStats.getValue()) {
                            return result.parseTotalStats();
                        } else if (result.command == Message.Command.RealTimeInfo.getValue()) {
                            return result.parseRealTimeInfo();
                        } else {
                            Timber.i("Get unknown command: %02X", result.command);
                        }
                    }
                }
            }
        }
        return false;
    }

    public static InmotionAdapterV2 getInstance() {
        if (INSTANCE == null) {
            Timber.i("New instance");
            INSTANCE = new InmotionAdapterV2();
        }
        Timber.i("Get instance");
        return INSTANCE;

    }

	public void startKeepAliveTimer() {
        updateStep = 0;
        stateCon = 0;
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                if (updateStep == 0) {
                    if (stateCon == 0) {
                        if (WheelData.getInstance().bluetoothCmd(Message.getCarType().writeBuffer())) {
                            Timber.i("Sent car type message");
                        } else updateStep = 35;

                    } else if (stateCon == 1) {
                        if (WheelData.getInstance().bluetoothCmd(Message.getSerialNumber().writeBuffer())) {
                            Timber.i("Sent s/n message");
                        } else updateStep = 35;

                    } else if (stateCon == 2) {
                        if (WheelData.getInstance().bluetoothCmd(Message.getVersions().writeBuffer())) {
                            stateCon += 1;
                            Timber.i("Sent versions message");
                        } else updateStep = 35;

                    } else if (settingCommandReady) {
    					if (WheelData.getInstance().bluetoothCmd(settingCommand)) {
                            settingCommandReady = false;
                            requestSettings = true;
                            Timber.i("Sent command message");
                        } else updateStep = 35; // after +1 and %10 = 0
    				} else if (stateCon == 3 | requestSettings) {
                        if (WheelData.getInstance().bluetoothCmd(Message.getCurrentSettings().writeBuffer())) {
                            stateCon += 1;
                            Timber.i("Sent unknown data message");
                        } else updateStep = 35;

                    }
                    else if (stateCon == 4) {
                        if (WheelData.getInstance().bluetoothCmd(Message.getUselessData().writeBuffer())) {
                            Timber.i("Sent useless data message");
                            stateCon += 1;
                        } else updateStep = 35;

                    }
                    else if (stateCon == 5) {
                        if (WheelData.getInstance().bluetoothCmd(Message.getStatistics().writeBuffer())) {
                            Timber.i("Sent statistics data message");
                            stateCon += 1;
                        } else updateStep = 35;

                    }
                    else  {
                        if (WheelData.getInstance().bluetoothCmd(InmotionAdapterV2.Message.getRealTimeData().writeBuffer())) {
                            Timber.i("Sent realtime data message");
                            stateCon = 5;
                        } else updateStep = 35;

                    }


				}
                updateStep += 1;
                updateStep %= 10;
                Timber.i("Step: %d", updateStep);
            }
        };
        keepAliveTimer = new Timer();
        keepAliveTimer.scheduleAtFixedRate(timerTask, 100, 25);
    }

    @Override
    public void wheelBeep() {
        settingCommand = InmotionAdapterV2.Message.playSound(0x18).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void switchFlashlight() {
        boolean light = !WheelLog.AppConfig.getLightEnabled();
        WheelLog.AppConfig.setLightEnabled(light);
        setLightState(light);
    }

    @Override
    public void setLightState(final boolean lightEnable) {
        settingCommand = InmotionAdapterV2.Message.setLight(lightEnable).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setHandleButtonState(final boolean handleButtonEnable) {
        settingCommand = InmotionAdapterV2.Message.setHandleButton(handleButtonEnable).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setRideMode(final boolean rideMode) {
        settingCommand = InmotionAdapterV2.Message.setClassicMode(rideMode).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setSpeakerVolume(final int speakerVolume) {
        settingCommand = InmotionAdapterV2.Message.setVolume(speakerVolume).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setPedalTilt(final int angle) {
        settingCommand = InmotionAdapterV2.Message.setPedalTilt(angle).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setPedalSensivity(final int sensivity) {
        settingCommand = InmotionAdapterV2.Message.setPedalSensivity(sensivity).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void wheelCalibration() {
        settingCommand = InmotionAdapterV2.Message.wheelCalibration().writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setLockMode(final boolean lockMode) {
        settingCommand = InmotionAdapterV2.Message.setLock(lockMode).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setTransportMode(final boolean transportMode) {
        settingCommand = InmotionAdapterV2.Message.setTransportMode(transportMode).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setDrl(final boolean drl) {
        settingCommand = InmotionAdapterV2.Message.setDrl(drl).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setGoHomeMode(final boolean goHomeMode) {
        settingCommand = InmotionAdapterV2.Message.setGoHome(goHomeMode).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setFancierMode(final boolean fancierMode) {
        settingCommand = InmotionAdapterV2.Message.setFancierMode(fancierMode).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setMute(final boolean mute) {
        settingCommand = InmotionAdapterV2.Message.setMute(mute).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setFanQuiet(final boolean fanQuiet) {
        settingCommand = InmotionAdapterV2.Message.setQuietMode(fanQuiet).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setFan(final boolean fan) {
        settingCommand = InmotionAdapterV2.Message.setFan(fan).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void setLightBrightness(final int lightBrightness) {
        settingCommand = InmotionAdapterV2.Message.setLightBrightness(lightBrightness).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void updateMaxSpeed(final int maxSpeed) {
        settingCommand = InmotionAdapterV2.Message.setMaxSpeed(maxSpeed).writeBuffer();
        settingCommandReady = true;
    }

    @Override
    public void powerOff() {
        settingCommand = InmotionAdapterV2.Message.wheelOffFirstStage().writeBuffer();
        turningOff = true;
        settingCommandReady = true;
    }


    public static class Message {

        enum Flag {
            NoOp(0),
            Initial(0x11),
            Default(0x14);

            private final int value;

            Flag(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }

        enum Command {
            NoOp(0),
            MainVersion(0x01),
            MainInfo(0x02),
            Diagnistic(0x03),
            RealTimeInfo(0x04),
            BatteryRealTimeInfo(0x05),
            Something1(0x10),
            TotalStats(0x11),
            Settings(0x20),
            Control(0x60);


            private final int value;

            Command(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }

        int flags = Flag.NoOp.getValue();
        int len = 0;
        int command = 0;
        byte[] data;

        Message(byte[] bArr) {
            if (bArr.length < 5) return;
            flags = bArr[2];
            len = bArr[3];
            command = bArr[4] & 0x7F;
            if (len > 1) {
                data = Arrays.copyOfRange(bArr, 5, len+4);
            }
        }

        private Message() {

        }

        boolean parseMainData(){
            Timber.i("Parse main data");
            WheelData wd = WheelData.getInstance();
            wd.resetRideTime();
            if ((data[0] == (byte) 0x01) && len >= 6) {
                stateCon += 1;
                Timber.i("Parse car type");
                int mainSeries = data[1]; //02
                int series = data[2];    // 06
                int type = data[3];      // 01
                int batch = data[4];     // 02
                int feature = data[5];   // 01
                int reverse = data[6];   // 00
                wd.setModel("Inmotion V11");
                wd.setVersion(String.format(Locale.ENGLISH,"rev: %d.%d",batch,feature));
            } else if ((data[0] == (byte) 0x02) && len >= 17) {
                stateCon += 1;
                Timber.i("Parse serial num");
                String serialNumber;
                serialNumber = new String(data, 1, 16);

                wd.setSerial(serialNumber);
            } else if ((data[0] == (byte) 0x06) && len >= 10) {
                Timber.i("Parse versions");
            }
            return false;
        }

        boolean parseBatteryRealTimeInfo(){
            int bat1Voltage = MathsUtil.shortFromBytesLE(data, 0);
            int bat1Temp = data[4];
            int bat1ValidStatus = data[5] & 1;
            int bat1Enabled = (data[5] >> 1) & 1;
            int bat1WorkStatus1 = data[6] & 1;
            int bat1WorkStatus2 = (data[6] >> 1) & 1;
            int bat2Voltage = MathsUtil.shortFromBytesLE(data, 8);
            int bat2Temp = data[12];
            int bat2ValidStatus = data[13] & 1;
            int bat2Enabled = (data[13] >> 1) & 1;
            int bat2WorkStatus1 = data[14] & 1;
            int bat2WorkStatus2 = (data[14] >> 1) & 1;
            int chargeVoltage = MathsUtil.shortFromBytesLE(data, 16);
            int chargeCurrent = MathsUtil.shortFromBytesLE(data, 18);
            return false;
        }
        
        boolean parseDiagnostic(){
            boolean ok = true;
            if (data.length > 7)
                for (byte c : data) {
                    if (c != 0) {
                        ok = false;
                        break;
                    }
                }
            return false;
        }

        boolean parseSettings(){
            Timber.i("Parse settings data");
            int i = 1;
            int mSpeedLim = MathsUtil.shortFromBytesLE(data, i);
            int mPitchAngleZero = MathsUtil.signedShortFromBytesLE(data, i+2);
            int mDriveMode = data[i+4] & 0xF;
            int mRideMode = data[i+4] >> 4;
            int mComfSens = data[i + 5];
            int mClassSens = data[i + 6];
            int mVolume = data[i + 7];
            int mAudioId = MathsUtil.intFromBytesLE(data, i+8);
            int mStandByTime = MathsUtil.shortFromBytesLE(data, i+12);
            int mDecorLightMode = data[i + 14];
            int mAutoLightLowThr = data[i + 15];
            int mAutoLightHighThr = data[i + 16];
            int mLightBr = data[i + 17];
            int mAudioState = data[i + 20] & 3;
            int mDecorState = (data[i + 20]>>2) & 3;
            int mLiftedState = (data[i + 20] >> 4) & 3;
            int mAutoLightState = (data[i + 20] >> 6) & 3;
            int mAutoLightBrState = data[i + 21] & 3;
            int mLockState = (data[i + 21]>>2) & 3;
            int mTranspMode = (data[i + 21] >> 4) & 3;
            int mLoadDetect = (data[i + 21] >> 6) & 3;
            int mNoLoadDetect = data[i + 22] & 3;
            int mLowBat = (data[i + 22]>>2) & 3;
            int mFanQuiet = (data[i + 22] >> 4) & 3;
            int mFan = (data[i + 22] >> 6) & 3; // to test
            int mSome1 = data[i + 23] & 3; // to test
            int mSome2 = (data[i + 23]>>2) & 3; // to test
            int mSome3 = (data[i + 23] >> 4) & 3; // to test
            int mSome4 = (data[i + 23] >> 6) & 3; // to test
            WheelLog.AppConfig.setPedalsAdjustment(mPitchAngleZero/10);
            WheelLog.AppConfig.setWheelMaxSpeed(mSpeedLim/100);
            WheelLog.AppConfig.setFancierMode(mRideMode != 0);
            WheelLog.AppConfig.setRideMode(mDriveMode != 0);
            WheelLog.AppConfig.setPedalSensivity(mComfSens);
            WheelLog.AppConfig.setSpeakerVolume(mVolume);
            WheelLog.AppConfig.setLightBrightness(mLightBr);
            WheelLog.AppConfig.setSpeakerMute(mAudioState == 0);
            WheelLog.AppConfig.setDrlEnabled(mDecorState != 0);
            WheelLog.AppConfig.setHandleButtonDisabled(mLiftedState == 0);
            WheelLog.AppConfig.setLockMode(mLockState != 0);
            WheelLog.AppConfig.setTransportMode(mTranspMode != 0);
            WheelLog.AppConfig.setFanQuietEnabled(mFanQuiet != 0);
            WheelLog.AppConfig.setGoHomeMode(mLowBat != 0);
            return false;
        }

        boolean parseTotalStats() {
            Timber.i("Parse total stats data");
            WheelData wd = WheelData.getInstance();
            long mTotal = MathsUtil.intFromBytesLE(data, 0);
            long mTotal2 = MathsUtil.getInt4(data, 0);
            long mDissipation = MathsUtil.intFromBytesLE(data, 4);
            long mRecovery = MathsUtil.intFromBytesLE(data, 8);
            long mRideTime = MathsUtil.intFromBytesLE(data, 12);
            int sec = (int)(mRideTime % 60);
            int min = (int)((mRideTime / 60) % 60);
            int hour = (int) (mRideTime/ 3600);
            String mRideTimeStr = String.format("%d:%02d:%02d",hour,min,sec);
            long mPowerOnTime = MathsUtil.intFromBytesLE(data, 16);
            sec = (int)(mPowerOnTime % 60);
            min = (int)((mPowerOnTime / 60) % 60);
            hour = (int) (mPowerOnTime/ 3600);
            String mPowerOnTimeStr = String.format("%d:%02d:%02d",hour,min,sec);
            wd.setTotalDistance(mTotal*10);
            return false;
        }

        boolean parseRealTimeInfo() {
            Timber.i("Parse realtime stats data");
            WheelData wd = WheelData.getInstance();
            int mVoltage = MathsUtil.shortFromBytesLE(data, 0);
            //int mVoltage2 = MathsUtil.getInt2R(data, 0); looks ok
            int mCurrent = MathsUtil.signedShortFromBytesLE(data, 2);
            int mSpeed = MathsUtil.signedShortFromBytesLE(data, 4);
            int mTorque = MathsUtil.signedShortFromBytesLE(data, 6);
            int mBatPower = MathsUtil.signedShortFromBytesLE(data, 8);
            int mMotPower = MathsUtil.signedShortFromBytesLE(data, 10);
            int mMileage = MathsUtil.shortFromBytesLE(data, 12) * 10;
            int mRemainMileage = MathsUtil.shortFromBytesLE(data, 14) * 10;
            int mBatLevel = data[16] & 0x7f;
            int mBatMode = (data[16] >> 7)  & 0x1;
            int mMosTemp = (data[17] & 0xff) + 80 - 256;
            int mMotTemp = (data[18] & 0xff) + 80 - 256;
            int mBatTemp = (data[19] & 0xff) + 80 - 256;
            int mBoardTemp = (data[20] & 0xff) + 80 - 256;
            int mLampTemp = (data[21] & 0xff) + 80 - 256;
            int mPitchAngle = MathsUtil.signedShortFromBytesLE(data, 22);
            int mPitchAimAngle = MathsUtil.signedShortFromBytesLE(data, 24);
            int mRollAngle = MathsUtil.signedShortFromBytesLE(data, 26);
            int mDynamicSpeedLimit = MathsUtil.shortFromBytesLE(data, 28);
            int mDynamicCurrentLimit = MathsUtil.shortFromBytesLE(data, 30);
            int mBrightness = data[32]& 0xff;
            int mLightBrightness = data[33]& 0xff;
            int mCpuTemp = (data[34] & 0xff) + 80 - 256;
            int mImuTemp = (data[35] & 0xff) + 80 - 256;
            wd.setVoltage(mVoltage);
            wd.setTorque((double)mTorque/100.0);
            wd.setMotorPower(mMotPower);
            wd.setCpuTemp(mCpuTemp);
            wd.setImuTemp(mImuTemp);
            wd.setCurrent(mCurrent);
            wd.setSpeed(mSpeed);
            wd.setCurrentLimit((double)mDynamicCurrentLimit/100.0);
            wd.setSpeedLimit((double)mDynamicSpeedLimit/100.0);
            wd.setBatteryPercent(mBatLevel);
            wd.setTemperature(mMosTemp * 100);
            wd.setTemperature2(mBoardTemp * 100);
            wd.setAngle((double)mPitchAngle/100.0);
            wd.setRoll((double)mRollAngle/100.0);
            wd.updateRideTime();
            wd.setTopSpeed(mSpeed);
            wd.setVoltageSag(mVoltage);
            wd.setPower(mBatPower);
            wd.setWheelDistance(mMileage);
            //// state data
            int mPcMode = data[36] & 0x07; // lock, drive, shutdown, idle
            int mMcMode = (data[36]>>3)&0x07;
            int mMotState = (data[36]>>6)&0x01;
            int chrgState = (data[36]>>7)&0x01;
            int lightState = (data[37])&0x01;
            int decorLiState = (data[37] >> 1) & 0x01;
            int liftedState = (data[37]>>2)&0x01;
            int tailLiState = (data[37]>>3)&0x03;
            int fanState = (data[37]>>5)&0x01;
            String wmode = "";
            if (mMotState == 1) {wmode = wmode + "Active";}
            if (chrgState == 1) {wmode = wmode + " Charging";}
            if (liftedState == 1) {wmode = wmode + " Lifted";}
            wd.setModeStr(wmode);
            //WheelLog.AppConfig.setFanEnabled(fanState != 0); // bad behaviour
            WheelLog.AppConfig.setLightEnabled(lightState != 0);
            //WheelLog.AppConfig.setDrlEnabled(decorLiState != 0); // too fast, bad behaviour

            //// rest data


            return true;
        }

        public static Message getCarType() {
            Message msg = new Message();
            msg.flags = Flag.Initial.getValue();
            msg.command = Command.MainInfo.getValue();
            msg.data = new byte[]{(byte)0x01};
            return msg;
        }

        public static Message getMainVersion() {
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.MainVersion.getValue();
            msg.data = new byte[0];
            return msg;
        }

        public static Message wheelOffFirstStage() {
            Message msg = new Message();
            msg.flags = Flag.Initial.getValue();
            msg.command = Command.Diagnistic.getValue();
            msg.data = new byte[]{(byte)0x81, (byte) 0x00};
            return msg;
        }

        public static Message wheelOffSecondStage() {
            Message msg = new Message();
            msg.flags = Flag.Initial.getValue();
            msg.command = Command.Diagnistic.getValue();
            msg.data = new byte[]{(byte)0x82};
            return msg;
        }

        public static Message getSerialNumber() {
            Message msg = new Message();
            msg.flags = Flag.Initial.getValue();
            msg.command = Command.MainInfo.getValue();
            msg.data = new byte[]{(byte)0x02};
            return msg;
        }

        public static Message getVersions() {
            Message msg = new Message();
            msg.flags = Flag.Initial.getValue();
            msg.command = Command.MainInfo.getValue();
            msg.data = new byte[]{(byte)0x06};
            return msg;
        }

        public static Message getCurrentSettings() {
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Settings.getValue();
            msg.data = new byte[]{(byte)0x20};
            return msg;
        }

        public static Message getUselessData() {
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Something1.getValue();
            msg.data = new byte[]{(byte)0x00, (byte)0x01};
            return msg;
        }

        public static Message getBatteryData() {
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.BatteryRealTimeInfo.getValue();
            msg.data = new byte[0];
            return msg;
        }

        public static Message getDiagnostic() {
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Diagnistic.getValue();
            msg.data = new byte[0];
            return msg;
        }

        public static Message getStatistics() {
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.TotalStats.getValue();
            msg.data = new byte[0];
            return msg;
        }

        public static Message getRealTimeData() {
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.RealTimeInfo.getValue();
            msg.data = new byte[0];
            return msg;
        }

        public static Message playSound(int number) {
            byte value = (byte)(number & 0xFF);
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x41, value, 0x01};
            return msg;
        }

        public static Message wheelCalibration() {
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x42, 0x01, 0x00, 0x01};
            return msg;
        }

        public static Message setLight(boolean on) {
            byte enable = 0;
            if (on) enable = 1;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x40, enable};
            return msg;
        }

        public static Message setLightBrightness(int brightness) {
            byte value = (byte)(brightness & 0xFF);
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x2b, value};
            return msg;
        }

        public static Message setVolume(int volume) {
            byte value = (byte)(volume & 0xFF);
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x26, value};
            return msg;
        }

        public static Message setDrl(boolean on) {
            byte enable = 0;
            if (on) enable = 1;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x2d, enable};
            return msg;
        }

        public static Message setHandleButton(boolean on) {
            byte enable = 0;
            if (!on) enable = 1;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x2e, enable};
            return msg;
        }

        public static Message setFan(boolean on) {
            byte enable = 0;
            if (on) enable = 1;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x43, enable};
            return msg;
        }

        public static Message setQuietMode(boolean on) {
            byte enable = 0;
            if (on) enable = 1;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x38, enable};
            return msg;
        }

        public static Message setFancierMode(boolean on) {
            byte enable = 0;
            if (on) enable = 1;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x24, enable};
            return msg;
        }

        public static Message setMaxSpeed(int maxSpeed) {
            byte[] value = MathsUtil.getBytes((short)(maxSpeed * 100));
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x21, value[1], value[0]};
            return msg;
        }

        public static Message setPedalSensivity(int sensivity) {
            byte value = (byte)(sensivity & 0xFF);
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x25, value, 0x64};
            return msg;
        }

        public static Message setClassicMode(boolean on) {
            byte enable = 0;
            if (on) enable = 1;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x23, enable};
            return msg;
        }

        public static Message setGoHome(boolean on) {
            byte enable = 0;
            if (on) enable = 1;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x37, enable};
            return msg;
        }

        public static Message setTransportMode(boolean on) {
            byte enable = 0;
            if (on) enable = 1;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x32, enable};
            return msg;
        }

        public static Message setLock(boolean on) {
            byte enable = 0;
            if (on) enable = 1;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x31, enable};
            return msg;
        }

        public static Message setMute(boolean on) {
            byte enable = 1;
            if (on) enable = 0;
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x2c, enable};
            return msg;
        }

        public static Message setPedalTilt(int angle) {
            byte[] value = MathsUtil.getBytes((short)(angle * 10));
            Message msg = new Message();
            msg.flags = Flag.Default.getValue();
            msg.command = Command.Control.getValue();
            msg.data = new byte[]{0x22, value[1], value[0]};
            return msg;
        }




        public byte[] writeBuffer() {

            byte[] buffer = getBytes();
            byte check = calcCheck(buffer);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(0xAA);
            out.write(0xAA);
            try {
                out.write(escape(buffer));
            } catch (IOException e) {
                e.printStackTrace();
            }
            out.write(check);
            return out.toByteArray();
        }

        private byte[] getBytes() {

            ByteArrayOutputStream buff = new ByteArrayOutputStream();
            buff.write(flags);
            buff.write(data.length+1);
            buff.write(command);
            try {
                buff.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return buff.toByteArray();
        }

        private static byte calcCheck(byte[] buffer) {

            int check = 0;
            for (byte c : buffer) {
                check = (check ^ c) & 0xFF;
            }
            return (byte) check;
        }

        static Message verify(byte[] buffer) {
            Timber.i("Verify: %s", StringUtil.toHexString(buffer));
            byte[] dataBuffer = Arrays.copyOfRange(buffer, 0, buffer.length - 1);
            byte check = calcCheck(dataBuffer);

            byte bufferCheck = buffer[buffer.length - 1];
            if (check == bufferCheck) {
                Timber.i("Check OK");
            } else {
                Timber.i("Check FALSE, calc: %02X, packet: %02X",check, bufferCheck);
            }
            return (check == bufferCheck) ? new Message(dataBuffer) : null;
        }

        private byte[] escape(byte[] buffer) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte c : buffer) {
                if (c == (byte) 0xAA || c == (byte) 0xA5) {
                    out.write(0xA5);
                }
                out.write(c);
            }
            return out.toByteArray();
        }
    }
	
    static class InmotionUnpackerV2 {

        enum UnpackerState {
            unknown,
            flagsearch,
            lensearch,
            collecting,
            done
        }


        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int oldc = 0;
        int len = 0;
        int flags = 0;
        UnpackerState state = UnpackerState.unknown;

        byte[] getBuffer() {
            return buffer.toByteArray();
        }

        boolean addChar(int c) {
            if (c != (byte)0xA5 || oldc == (byte)0xA5){

                switch (state) {

                    case collecting:

                        buffer.write(c);
                        if (buffer.size() == len + 5) {
                            state = UnpackerState.done;
                            updateStep = 0;
                            oldc = 0;
                            Timber.i("Len %d", len);
                            Timber.i("Step reset");
                            return true;
                        }
                        break;

                    case lensearch:
                        buffer.write(c);
                        len = c & 0xff;
                        state = UnpackerState.collecting;
                        oldc = c;
                        break;

                    case flagsearch:
                        buffer.write(c);
                        flags = c & 0xff;
                        state = UnpackerState.lensearch;
                        oldc = c;
                        break;

                    default:
                        if (c == (byte) 0xAA && oldc == (byte) 0xAA) {
                            buffer = new ByteArrayOutputStream();
                            buffer.write(0xAA);
                            buffer.write(0xAA);
                            state = UnpackerState.flagsearch;
                        }
                        oldc = c;
                }

            } else {
                oldc = c;
            }
            return false;
        }

        void reset() {
            buffer = new ByteArrayOutputStream();
            oldc = 0;
            state = UnpackerState.unknown;

        }
    }

    @Override
    public int getCellSForWheel() {
        return 20;
    }

    public static void newInstance() {
        if (INSTANCE != null && INSTANCE.keepAliveTimer != null) {
            INSTANCE.keepAliveTimer.cancel();
            INSTANCE.keepAliveTimer = null;
        }
        Timber.i("New instance");
        INSTANCE = new InmotionAdapterV2();
    }


    public static void stopTimer() {
        if (INSTANCE != null && INSTANCE.keepAliveTimer != null) {
            INSTANCE.keepAliveTimer.cancel();
            INSTANCE.keepAliveTimer = null;
        }
        Timber.i("Kill instance, stop timer");
        INSTANCE = null;
    }

}


