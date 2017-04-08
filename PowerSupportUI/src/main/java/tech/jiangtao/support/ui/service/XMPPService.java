package tech.jiangtao.support.ui.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.widget.RemoteViews;

import io.realm.Realm;
import io.realm.RealmResults;

import java.util.UUID;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import tech.jiangtao.support.kit.archive.type.MessageAuthor;
import tech.jiangtao.support.kit.archive.type.MessageExtensionType;
import tech.jiangtao.support.kit.callback.DisconnectCallBack;
import tech.jiangtao.support.kit.eventbus.DeleteVCardRealm;
import tech.jiangtao.support.kit.eventbus.FriendRequest;
import tech.jiangtao.support.kit.eventbus.RecieveLastMessage;
import tech.jiangtao.support.kit.eventbus.RecieveMessage;
import tech.jiangtao.support.kit.eventbus.UnRegisterEvent;
import tech.jiangtao.support.kit.realm.MessageRealm;
import tech.jiangtao.support.kit.realm.SessionRealm;
import tech.jiangtao.support.kit.realm.VCardRealm;
import tech.jiangtao.support.kit.util.LogUtils;
import tech.jiangtao.support.kit.util.StringSplitUtil;
import tech.jiangtao.support.ui.R;
import tech.jiangtao.support.ui.SupportAIDLConnection;
import tech.jiangtao.support.ui.activity.ChatActivity;
import tech.jiangtao.support.ui.activity.NewFriendActivity;
import tech.jiangtao.support.ui.fragment.ChatFragment;
import tech.jiangtao.support.ui.reciever.TickBroadcastReceiver;
import tech.jiangtao.support.ui.utils.ServiceUtils;
import xiaofei.library.hermeseventbus.HermesEventBus;

/**
 * Class: XMPPService </br>
 * Description: 进行数据库操作和通知的服务 </br>
 * 简单的进行双进程守护
 * Creator: kevin </br>
 * Email: jiangtao103cp@gmail.com </br>
 * Date: 31/12/2016 1:54 AM</br>
 * Update: 31/12/2016 1:54 AM </br>
 * mRealm 有泄漏
 **/

public class XMPPService extends Service {

  public static final String TAG = XMPPService.class.getSimpleName();
  private static final int NOTIFICATION_ID = 1017;
  @SuppressLint("StaticFieldLeak") private static Realm mRealm;
  private XMPPServiceConnection mXMPPServiceConnection;
  private XMPPBinder mXMPPBinder;
  private PowerManager.WakeLock mWakelock;

  @Override public void onCreate() {
    super.onCreate();
    if (mXMPPBinder == null) {
      mXMPPBinder = new XMPPBinder();
    }
    PowerManager mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
    mWakelock = mPowerManager.newWakeLock(
        PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, "target");
    mXMPPServiceConnection = new XMPPServiceConnection();
    if (!HermesEventBus.getDefault().isRegistered(this)) {
      HermesEventBus.getDefault().register(this);
    }
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    startForegroundCompat();
    if (mRealm == null || mRealm.isClosed()) {
      mRealm = Realm.getDefaultInstance();
    }
    IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
    TickBroadcastReceiver receiver = new TickBroadcastReceiver();
    registerReceiver(receiver, filter);
    Intent intent1 = new Intent(this, SupportService.class);
    this.bindService(intent1, mXMPPServiceConnection, Context.BIND_IMPORTANT);
    return START_STICKY;
  }

  @Subscribe(threadMode = ThreadMode.MAIN) public void onRecieveMessage(RecieveMessage message) {
    //先保存会话表，然后保存到消息记录表
    // TODO: 2017/1/5 此处已经把我绕晕了,保存数据库有问题
    if (mRealm == null || mRealm.isClosed()) {
      mRealm = Realm.getDefaultInstance();
    }
    mRealm.executeTransactionAsync(realm -> {
      RealmResults<SessionRealm> result = null;
      if (message.messageAuthor == MessageAuthor.FRIEND) {
        result = realm.where(SessionRealm.class)
            .equalTo("user_from", StringSplitUtil.splitDivider(message.userJID))
            .equalTo("user_to", StringSplitUtil.splitDivider(message.ownJid))
            .findAll();
      } else {
        result = realm.where(SessionRealm.class)
            .equalTo("user_from", StringSplitUtil.splitDivider(message.ownJid))
            .equalTo("user_to", StringSplitUtil.splitDivider(message.userJID))
            .findAll();
      }
      SessionRealm sessionRealm;
      if (result.size() != 0) {
        sessionRealm = result.first();
        sessionRealm.setMessage_id(message.id);
        sessionRealm.setUnReadCount(sessionRealm.getUnReadCount() + 1);
      } else {
        sessionRealm = new SessionRealm();
        sessionRealm.setSession_id(UUID.randomUUID().toString());
        if (message.messageAuthor == MessageAuthor.FRIEND) {
          sessionRealm.setUser_from(StringSplitUtil.splitDivider(message.userJID));
          sessionRealm.setUser_to(StringSplitUtil.splitDivider(message.ownJid));
          sessionRealm.setVcard_id(StringSplitUtil.splitDivider(message.userJID));
        } else {
          sessionRealm.setUser_from(StringSplitUtil.splitDivider(message.ownJid));
          sessionRealm.setUser_to(StringSplitUtil.splitDivider(message.userJID));
          sessionRealm.setVcard_id(StringSplitUtil.splitDivider(message.ownJid));
        }
        sessionRealm.setMessage_id(message.id);
        sessionRealm.setUnReadCount(1);
      }
      MessageRealm messageRealm = new MessageRealm();
      messageRealm.setId(message.id);
      messageRealm.setMainJID(StringSplitUtil.splitDivider(message.userJID));
      messageRealm.setWithJID(StringSplitUtil.splitDivider(message.ownJid));
      messageRealm.setTextMessage(message.message);
      messageRealm.setTime(null);
      messageRealm.setThread(message.thread);
      messageRealm.setType(message.type.toString());
      messageRealm.setMessageType(message.messageType.toString());
      messageRealm.setMessageStatus(false);
      realm.copyToRealmOrUpdate(sessionRealm);
      realm.copyToRealm(messageRealm);
    }, () -> {
      LogUtils.d(TAG, "onSuccess: 保存消息成功");
      HermesEventBus.getDefault()
          .post(new RecieveLastMessage(message.id, message.type, message.userJID, message.ownJid,
              message.thread, message.message, message.messageType, false, message.messageAuthor));
      //查询VCard
      Intent intent = null;
      RealmResults<VCardRealm> results = mRealm.where(VCardRealm.class)
          .equalTo("jid", StringSplitUtil.splitDivider(message.userJID))
          .findAll();
      if (results.size() != 0) {
        intent = new Intent(XMPPService.this, ChatActivity.class);
        intent.putExtra(ChatActivity.VCARD, results.first());
      }
      LogUtils.d(TAG, "当前应用是否处于前台"
          + ServiceUtils.isApplicationBroughtToBackground(this.getApplicationContext())
          + "");
      if (message.messageAuthor == MessageAuthor.FRIEND && intent != null) {
        if (message.messageType == MessageExtensionType.TEXT) {
          showOnesNotification(StringSplitUtil.splitPrefix(message.userJID), message.message,
              intent);
          LogUtils.d(TAG, "显示通知");
          //保存到本地数据库
        }
        if (message.messageType == MessageExtensionType.IMAGE) {
          showOnesNotification(StringSplitUtil.splitPrefix(message.userJID), "[图片]", intent);
          //保存到本地数据库
        }
        if (message.messageType == MessageExtensionType.AUDIO) {
          showOnesNotification(StringSplitUtil.splitPrefix(message.userJID), "[音频]", intent);
          //保存到本地数据库
        }
        if (message.messageType == MessageExtensionType.VIDEO) {
          showOnesNotification(StringSplitUtil.splitPrefix(message.userJID), "[视频]", intent);
          //保存到本地数据库
        }
      }
    }, error -> LogUtils.d(TAG, "onError: 保存消息失败" + error.getMessage()));
  }

  /**
   * 添加好友通知
   */
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN) @Subscribe(threadMode = ThreadMode.MAIN)
  public void addFriendsNotification(FriendRequest request) {
    mWakelock.acquire();
    Intent i = new Intent(this, NewFriendActivity.class);
    i.putExtra(NewFriendActivity.NEW_FLAG, request);
    showOnesNotification(request.username, "有一个添加好友请求", i);
    mWakelock.release();
  }

  @Subscribe(threadMode = ThreadMode.MAIN) public void onVCardRealmMessage(VCardRealm realmObject) {
    if (mRealm == null || mRealm.isClosed()) {
      mRealm = Realm.getDefaultInstance();
    }
    mRealm.executeTransactionAsync(realm -> {
      RealmResults<VCardRealm> result = realm.where(VCardRealm.class)
          .equalTo("jid", StringSplitUtil.splitDivider(realmObject.getJid()))
          .findAll();
      if (result.size() != 0) {
        VCardRealm realmUpdate = result.first();
        realmUpdate.setNickName(realmObject.getNickName());
        realmUpdate.setSex(realmObject.getSex());
        realmUpdate.setSubject(realmObject.getSubject());
        realmUpdate.setOffice(realmObject.getOffice());
        realmUpdate.setEmail(realmObject.getEmail());
        realmUpdate.setPhoneNumber(realmObject.getPhoneNumber());
        realmUpdate.setSignature(realmObject.getSignature());
        realmUpdate.setAvatar(realmObject.getAvatar());
        if (realmUpdate.getNickName() != null) {
          realmUpdate.setAllPinYin(realmObject.getAllPinYin());
          realmUpdate.setFirstLetter(realmObject.getFirstLetter());
        }
        realmUpdate.setFriend(true);
        LogUtils.d(TAG, "onVCardRealmMessage:更新数据 " + realmUpdate.toString());
      } else {
        LogUtils.d(TAG, "onVCardRealmMessage: " + realmObject.toString());
        realm.copyToRealm(realmObject);
      }
    }, () -> {
      LogUtils.d(TAG, "onSuccess: 执行成功");
      //发送消息更新，应该也可以不用发送消息
    }, error -> LogUtils.d(TAG, "onError: 通讯录后台执行错误，错误信息" + error.getMessage()));
  }

  /**
   * 显示通知
   */
  public void showNotification(String name, String info) {
    Notification.Builder builder = new Notification.Builder(this);
    Intent intent = new Intent(this, ChatFragment.class);
    builder.setContentIntent(
        PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT))
        .setContentTitle(name)
        .setContentText(info)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setWhen(System.currentTimeMillis())
        .setPriority(Notification.PRIORITY_HIGH)
        .setDefaults(Notification.DEFAULT_VIBRATE);
    Notification notification = builder.build();
    notification.flags = Notification.FLAG_ONGOING_EVENT;
    notification.defaults = Notification.DEFAULT_SOUND;
    startForeground(110, notification);
  }

  @Override public void onDestroy() {
    super.onDestroy();
    mRealm.close();
  }

  @Nullable @Override public IBinder onBind(Intent intent) {
    return mXMPPBinder;
  }

  public void showOnesNotification(String name, String info, Intent intent) {
    mWakelock.acquire();
    NotificationManager mNotificationManager =
        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    Notification.Builder builder = new Notification.Builder(this);
    builder.setContentIntent(
        PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT))
        .setContentTitle(name)
        .setContentText(info)
        .setSmallIcon(tech.jiangtao.support.kit.R.mipmap.ic_launcher)
        .setLargeIcon(BitmapFactory.decodeResource(this.getResources(),
            tech.jiangtao.support.kit.R.mipmap.ic_launcher))
        .setWhen(System.currentTimeMillis())
        .setPriority(Notification.PRIORITY_HIGH)
        .setDefaults(Notification.DEFAULT_VIBRATE);
    Notification notification = builder.build();
    notification.flags = Notification.FLAG_AUTO_CANCEL;
    notification.defaults = Notification.DEFAULT_SOUND;
    mNotificationManager.notify(0, notification);
    mWakelock.release();
  }

  /**
   * 删除用户，并且删除该用户的聊天用户
   */
  @Subscribe(threadMode = ThreadMode.MAIN) public void messageAchieve(
      DeleteVCardRealm deleteVCardRealm) {
    mRealm.executeTransactionAsync(realm -> {
      RealmResults<VCardRealm> results =
          realm.where(VCardRealm.class).equalTo("jid", deleteVCardRealm.jid).findAll();
      if (results.size() != 0) {
        results.deleteAllFromRealm();
      }
      RealmResults<SessionRealm> messageResult =
          realm.where(SessionRealm.class).equalTo("vcard_id", deleteVCardRealm.jid).findAll();
      if (messageResult.size() != 0) {
        messageResult.deleteAllFromRealm();
      }
    });
  }

  public static void disConnect(DisconnectCallBack callBack) {
    HermesEventBus.getDefault().post(new UnRegisterEvent());
    //删除数据库
    if (mRealm == null || mRealm.isClosed()) {
      mRealm = Realm.getDefaultInstance();
    }
    mRealm.executeTransactionAsync(realm -> {
      realm.deleteAll();
      callBack.disconnectFinish();
    });
  }


  /**
   * 保证连接的代码
   */
  private class XMPPServiceConnection implements ServiceConnection {

    @Override public void onServiceConnected(ComponentName name, IBinder service) {
      LogUtils.d(TAG, "onServiceConnected: 建立连接");
    }

    @Override public void onServiceDisconnected(ComponentName name) {
      LogUtils.d(TAG, "onServiceDisconnected: 服务被杀");
      XMPPService.this.startService(new Intent(XMPPService.this, SupportService.class));
      Intent intent = new Intent(XMPPService.this, SupportService.class);
      XMPPService.this.bindService(intent, mXMPPServiceConnection, Context.BIND_IMPORTANT);
    }
  }

  private class XMPPBinder extends SupportAIDLConnection.Stub {

    @Override public String getServiceName() throws RemoteException {
      return "XMPPService的服务";
    }
  }

  //防锁屏后系统休眠
  private static class InnerService extends Service {

    @Override public void onCreate() {
      super.onCreate();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
      startForeground(NOTIFICATION_ID, fadeNotification(this));
      stopForeground(true);
      stopSelf();
      return super.onStartCommand(intent, flags, startId);
    }

    @Nullable @Override public IBinder onBind(Intent intent) {
      return null;
    }
  }

  private static Notification fadeNotification(Context context) {
    Notification notification = new Notification();
    // 随便给一个icon，反正不会显示，只是假装自己是合法的Notification而已
    notification.icon = R.drawable.abc_ab_share_pack_mtrl_alpha;
    notification.priority = Notification.PRIORITY_MIN;
    notification.contentView =
        new RemoteViews(context.getPackageName(), R.layout.notification_view);
    return notification;
  }

  private void startForegroundCompat() {

    // api 18的时候，google管严了
    // 先把自己做成一个前台服务，提供合法的参数
    startService(new Intent(this, InnerService.class));
    startForeground(NOTIFICATION_ID, fadeNotification(this));
  }
}
