package tech.jiangtao.support.ui.fragment;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import com.melink.bqmmsdk.bean.Emoji;
import com.melink.bqmmsdk.sdk.BQMM;
import com.melink.bqmmsdk.sdk.IBqmmSendMessageListener;
import com.melink.bqmmsdk.ui.keyboard.BQMMKeyboard;
import com.melink.bqmmsdk.widget.BQMMEditView;
import com.melink.bqmmsdk.widget.BQMMSendButton;

import com.vincent.filepicker.Constant;
import com.vincent.filepicker.activity.AudioPickActivity;
import com.vincent.filepicker.activity.ImagePickActivity;
import com.vincent.filepicker.activity.NormalFilePickActivity;
import com.vincent.filepicker.activity.VideoPickActivity;
import com.vincent.filepicker.filter.entity.ImageFile;

import io.realm.Realm;
import io.realm.RealmResults;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import tech.jiangtao.support.kit.archive.type.FileType;
import tech.jiangtao.support.kit.archive.type.MessageAuthor;
import tech.jiangtao.support.kit.archive.type.MessageExtensionType;
import tech.jiangtao.support.kit.eventbus.RecieveLastMessage;
import tech.jiangtao.support.kit.eventbus.TextMessage;
import tech.jiangtao.support.kit.realm.MessageRealm;
import tech.jiangtao.support.kit.realm.SessionRealm;
import tech.jiangtao.support.kit.realm.VCardRealm;
import tech.jiangtao.support.kit.util.ErrorAction;
import tech.jiangtao.support.kit.util.StringSplitUtil;
import tech.jiangtao.support.ui.R;
import tech.jiangtao.support.ui.R2;
import tech.jiangtao.support.ui.adapter.BaseEasyAdapter;
import tech.jiangtao.support.ui.adapter.BaseEasyViewHolderFactory;
import tech.jiangtao.support.ui.adapter.ChatMessageAdapter;
import tech.jiangtao.support.ui.adapter.EasyViewHolder;
import tech.jiangtao.support.ui.api.ApiService;
import tech.jiangtao.support.ui.api.service.UpLoadServiceApi;
import tech.jiangtao.support.ui.model.ChatExtraModel;
import tech.jiangtao.support.ui.model.Message;
import tech.jiangtao.support.ui.model.type.MessageType;
import tech.jiangtao.support.ui.pattern.ConstructMessage;
import tech.jiangtao.support.ui.utils.CommonUtils;
import tech.jiangtao.support.ui.view.AudioManager;
import tech.jiangtao.support.ui.view.AudioRecordButton;
import tech.jiangtao.support.ui.viewholder.ExtraFuncViewHolder;
import work.wanghao.simplehud.SimpleHUD;
import xiaofei.library.hermeseventbus.HermesEventBus;

import static android.app.Activity.RESULT_OK;
import static android.content.ContentValues.TAG;
import static com.vincent.filepicker.activity.AudioPickActivity.IS_NEED_RECORDER;
import static com.vincent.filepicker.activity.VideoPickActivity.IS_NEED_CAMERA;

/**
 * Class: ChatFragment </br>
 * Description: 聊天页面 </br>
 * Creator: kevin </br>
 * Email: jiangtao103cp@gmail.com </br>
 * Date: 02/12/2016 11:40 AM</br>
 * Update: 02/12/2016 11:40 AM </br>
 **/
public class ChatFragment extends BaseFragment
    implements TextWatcher, View.OnClickListener, EasyViewHolder.OnItemClickListener,
    View.OnLongClickListener, AudioRecordButton.onAudioFinishRecordListener {

  @BindView(R2.id.recycler) RecyclerView mRecycler;
  @BindView(R2.id.swift_refresh) SwipeRefreshLayout mSwiftRefresh;
  @BindView(R2.id.chat_speak) ImageView mChatSpeak;
  @BindView(R2.id.chat_add_other_information) ImageView mChatAddOtherInformation;
  @BindView(R2.id.chat_send_message) BQMMSendButton mChatSendMessage;
  @BindView(R2.id.container_send) FrameLayout mContainerSend;
  @BindView(R2.id.add_smile) CheckBox mAddSmile;
  @BindView(R2.id.chat_input) BQMMEditView mChatInput;
  @BindView(R2.id.chat_inline_container) RelativeLayout mChatInlineContainer;
  @BindView(R2.id.chat_msg_input_box) BQMMKeyboard mChatMsgInputBox;
  @BindView(R2.id.chat_send_other) RecyclerView mChatSendOther;
  @BindView(R2.id.chat_bottom) RelativeLayout mChatBottom;
  @BindView(R2.id.chat_audio_record) AudioRecordButton mAudioRecord;
  private ChatMessageAdapter mChatMessageAdapter;
  private List<ConstructMessage> mMessages;
  private VCardRealm mVCardRealm;
  private VCardRealm mOwnVCardRealm;
  private Realm mRealm;
  private BQMM mBQMM;
  private UpLoadServiceApi mUpLoadServiceApi;

  public static ChatFragment newInstance() {
    return new ChatFragment();
  }

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    super.onCreateView(inflater, container, savedInstanceState);
    mRealm = Realm.getDefaultInstance();
    init();
    ButterKnife.bind(this, getView());
    return getView();
  }

  public void setExtraAdapter() {
    BaseEasyAdapter mBaseEasyAdapter = new BaseEasyAdapter(getContext());
    mBaseEasyAdapter.viewHolderFactory(new BaseEasyViewHolderFactory(getContext()));
    mBaseEasyAdapter.setOnClickListener((position, view) -> {
      ChatExtraModel model = (ChatExtraModel) mBaseEasyAdapter.get(position);
      if (model.name.equals("图片")) {
        Intent intent1 = new Intent(getContext(), ImagePickActivity.class);
        intent1.putExtra(IS_NEED_CAMERA, true);
        intent1.putExtra(Constant.MAX_NUMBER, 1);
        startActivityForResult(intent1, Constant.REQUEST_CODE_PICK_IMAGE);
      } else if (model.name.equals("文档")) {
        Intent intent4 = new Intent(getContext(), NormalFilePickActivity.class);
        intent4.putExtra(Constant.MAX_NUMBER, 1);
        intent4.putExtra(NormalFilePickActivity.SUFFIX,
            new String[] { "xlsx", "xls", "doc", "docx", "ppt", "pptx", "pdf" });
        startActivityForResult(intent4, Constant.REQUEST_CODE_PICK_FILE);
      } else if (model.name.equals("语音")) {
        Intent intent3 = new Intent(getContext(), AudioPickActivity.class);
        intent3.putExtra(IS_NEED_RECORDER, true);
        intent3.putExtra(Constant.MAX_NUMBER, 1);
        startActivityForResult(intent3, Constant.REQUEST_CODE_PICK_AUDIO);
      } else if (model.name.equals("视频")) {
        Intent intent2 = new Intent(getContext(), VideoPickActivity.class);
        intent2.putExtra(IS_NEED_CAMERA, true);
        intent2.putExtra(Constant.MAX_NUMBER, 1);
        startActivityForResult(intent2, Constant.REQUEST_CODE_PICK_VIDEO);
      }
    });
    mBaseEasyAdapter.bind(ChatExtraModel.class, ExtraFuncViewHolder.class);
    mChatSendOther.setLayoutManager(new GridLayoutManager(getContext(), 4));
    ArrayList<ChatExtraModel> mChatExtraItems = new ArrayList<>();
    mChatSendOther.setAdapter(mBaseEasyAdapter);
    mChatExtraItems.add(new ChatExtraModel(R.mipmap.ic_photo, "图片"));
    mChatExtraItems.add(new ChatExtraModel(R.mipmap.ic_location, "位置"));
    mChatExtraItems.add(new ChatExtraModel(R.mipmap.ic_call, "打电话"));
    mChatExtraItems.add(new ChatExtraModel(R.mipmap.ic_call, "视频"));
    mChatExtraItems.add(new ChatExtraModel(R.mipmap.ic_call, "文档"));
    mChatExtraItems.add(new ChatExtraModel(R.mipmap.ic_call, "语音"));
    mBaseEasyAdapter.addAll(mChatExtraItems);
    mBaseEasyAdapter.notifyDataSetChanged();
  }

  public void loadOwnRealm() {
    mMessages = new ArrayList<>();
    mVCardRealm = getArguments().getParcelable("vCard");
    String userJid = null;
    final AppPreferences appPreferences = new AppPreferences(getContext());
    try {
      userJid = appPreferences.getString("userJid");
    } catch (ItemNotFoundException e) {
      e.printStackTrace();
    }
    RealmResults<VCardRealm> realms = mRealm.where(VCardRealm.class)
        .equalTo("jid", StringSplitUtil.splitDivider(userJid))
        .findAll();
    if (realms.size() != 0) {
      mOwnVCardRealm = realms.first();
    }
    //jid中包含空和full jid,检查和进行处理
    RealmResults<MessageRealm> messageRealmse = mRealm.where(MessageRealm.class)
        .equalTo("mainJID", mVCardRealm.getJid())
        .or()
        .equalTo("withJID", mVCardRealm.getJid())
        .findAll();
    // TODO: 05/01/2017  获取最后20条,并且messageRealmse.get(i).getMainJID()解析反了
    for (int i = (messageRealmse.size() > 20 ? messageRealmse.size() - 20 : 0);
        i < messageRealmse.size(); i++) {
      if (StringSplitUtil.splitDivider(messageRealmse.get(i).getMainJID())
          .equals(StringSplitUtil.splitDivider(userJid))) {
        //自己的消息
        Message message1 = new Message();
        message1.paramContent = messageRealmse.get(i).getTextMessage();
        if (messageRealmse.get(i).getMessageType().equals(MessageExtensionType.TEXT.toString())) {
          message1.type = FileType.TYPE_TEXT;
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.TEXT_MESSAGE_MINE)
              .avatar(mOwnVCardRealm != null ? mOwnVCardRealm.getAvatar() : null)
              .message(message1)
              .build());
        } else if (messageRealmse.get(i)
            .getMessageType()
            .equals(MessageExtensionType.IMAGE.toString())) {
          message1.fimePath = CommonUtils.getUrl(MessageExtensionType.IMAGE.toString(),
              messageRealmse.get(i).getTextMessage());
          message1.type = FileType.TYPE_IMAGE;
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.IMAGE_MESSAGE_MINE)
              .avatar(mOwnVCardRealm != null && mOwnVCardRealm.getAvatar() != null
                  ? mOwnVCardRealm.getAvatar() : null)
              .message(message1)
              .build());
        } else if (messageRealmse.get(i)
            .getMessageType()
            .equals(MessageExtensionType.AUDIO.toString())) {
          message1.fimePath = CommonUtils.getUrl(MessageExtensionType.AUDIO.toString(),
              messageRealmse.get(i).getTextMessage());
          message1.time = 10;
          message1.type = FileType.TYPE_AUDIO;
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.AUDIO_MESSAGE_MINE)
              .avatar(mOwnVCardRealm != null && mOwnVCardRealm.getAvatar() != null
                  ? mOwnVCardRealm.getAvatar() : null)
              .message(message1)
              .build());
        }
      } else {
        //别人发送的消息
        Message message1 = new Message();
        message1.paramContent = messageRealmse.get(i).getTextMessage();
        if (messageRealmse.get(i).getMessageType().equals(MessageExtensionType.TEXT.toString())) {
          message1.paramContent = messageRealmse.get(i).getTextMessage();
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.TEXT_MESSAGE_OTHER)
              .avatar(mVCardRealm.getAvatar())
              .message(message1)
              .build());
        } else if (messageRealmse.get(i)
            .getMessageType()
            .equals(MessageExtensionType.IMAGE.toString())) {
          message1.fimePath = CommonUtils.getUrl(MessageExtensionType.IMAGE.toString(),
              messageRealmse.get(i).getTextMessage());
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.IMAGE_MESSAGE_OTHER)
              .avatar(mVCardRealm.getAvatar())
              .message(message1)
              .build());
        } else if (messageRealmse.get(i)
            .getMessageType()
            .equals(MessageExtensionType.AUDIO.toString())) {
          message1.fimePath = CommonUtils.getUrl(MessageExtensionType.AUDIO.toString(),
              messageRealmse.get(i).getTextMessage());
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.AUDIO_MESSAGE_OTHER)
              .avatar(mVCardRealm.getAvatar())
              .message(message1)
              .build());
        }
      }
    }
  }

  @Override public int layout() {
    return R.layout.fragment_chat;
  }

  private void init() {
    mUpLoadServiceApi = ApiService.getInstance().createApiService(UpLoadServiceApi.class);
    setUpBQMM();
    loadOwnRealm();
    setAdapter();
    setExtraAdapter();
    setViewListener();
  }

  private void setUpBQMM() {
    mBQMM = BQMM.getInstance();
    mBQMM.setEditView(mChatInput);
    mBQMM.setKeyboard(mChatMsgInputBox);
    mBQMM.setSendButton(mChatSendMessage);
    mBQMM.load();
    mChatInput.setOnTouchListener((v, event) -> {
      mAddSmile.setChecked(false);
      return false;
    });
    mBQMM.setBqmmSendMsgListener(new IBqmmSendMessageListener() {
      //图文混排消息
      @Override public void onSendMixedMessage(List<Object> list, boolean b) {
      }

      //单个大表情
      @Override public void onSendFace(Emoji emoji) {

      }
    });
  }

  private void setViewListener() {
    mChatInput.addTextChangedListener(this);
    mChatSpeak.setOnLongClickListener(this);
    mChatAddOtherInformation.setOnClickListener(this);
    mChatSendMessage.setOnClickListener(this);
    mAddSmile.setOnClickListener(this);
    mAudioRecord.setMonAudioFinishRecordListener(this);
  }

  public void setAdapter() {
    mChatMessageAdapter = new ChatMessageAdapter(getContext(), mMessages);
    mRecycler.setLayoutManager(
        new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
    mRecycler.setAdapter(mChatMessageAdapter);
    updateChatData();
  }

  @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {

  }

  @RequiresApi(api = Build.VERSION_CODES.KITKAT) @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
    if (s == null || Objects.equals(s.toString(), "")) {
      Log.e(TAG, "onTextChanged: ");
      mChatSendMessage.setVisibility(View.GONE);
      mChatAddOtherInformation.setVisibility(View.VISIBLE);
    } else {
      mBQMM.startShortcutPopupWindowByoffset(s.toString(), mChatSendMessage, 0, 20);
      mChatAddOtherInformation.setVisibility(View.GONE);
      mChatSendMessage.setVisibility(View.VISIBLE);
    }
  }

  @Override public void afterTextChanged(Editable s) {

  }

  @Subscribe(threadMode = ThreadMode.MAIN) public void onMessage(RecieveLastMessage message) {
    Log.d("----------->", "onMessage: " + message);
    // TODO: 04/01/2017  还需要添加一层判断，判断是否是当前用户的消息
    if (StringSplitUtil.splitDivider(message.userJID).equals(mVCardRealm.getJid())
        || StringSplitUtil.splitDivider(message.ownJid).equals(mVCardRealm.getJid())) {
      if (message.messageAuthor == MessageAuthor.FRIEND) {
        Message message1 = new Message();
        message1.paramContent = message.message;
        if (message.messageType == MessageExtensionType.TEXT) {
          message1.paramContent = message.message;
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.TEXT_MESSAGE_OTHER)
              .avatar(mVCardRealm.getAvatar())
              .message(message1)
              .build());
        } else if (message.messageType == MessageExtensionType.IMAGE) {
          message1.fimePath =
              CommonUtils.getUrl(MessageExtensionType.IMAGE.toString(), message.message);
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.IMAGE_MESSAGE_OTHER)
              .avatar(mVCardRealm.getAvatar())
              .message(message1)
              .build());
          Log.d(TAG, "onMessage: " + message1);
        } else if (message.messageType == MessageExtensionType.AUDIO) {
          message1.fimePath =
              CommonUtils.getUrl(MessageExtensionType.AUDIO.toString(), message.message);
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.AUDIO_MESSAGE_OTHER)
              .avatar(mVCardRealm.getAvatar())
              .message(message1)
              .build());
          Log.d(TAG, "onMessage: " + message1);
        }
      } else if (message.messageAuthor == MessageAuthor.OWN) {
        Message message2 = new Message();
        message2.paramContent = message.message;
        if (message.messageType == MessageExtensionType.TEXT) {
          message2.type = FileType.TYPE_TEXT;
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.TEXT_MESSAGE_MINE)
              .avatar(mOwnVCardRealm != null ? mOwnVCardRealm.getAvatar() : null)
              .message(message2)
              .build());
        } else if (message.messageType == MessageExtensionType.IMAGE) {
          message2.fimePath =
              CommonUtils.getUrl(MessageExtensionType.IMAGE.toString(), message.message);
          message2.type = FileType.TYPE_IMAGE;
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.IMAGE_MESSAGE_MINE)
              .avatar(mOwnVCardRealm != null && mOwnVCardRealm.getAvatar() != null
                  ? mOwnVCardRealm.getAvatar() : null)
              .message(message2)
              .build());
        } else if (message.messageType == MessageExtensionType.AUDIO) {
          message2.fimePath =
              CommonUtils.getUrl(MessageExtensionType.AUDIO.toString(), message.message);
          message2.time = 10;
          message2.type = FileType.TYPE_AUDIO;
          mMessages.add(new ConstructMessage.Builder().itemType(MessageType.AUDIO_MESSAGE_MINE)
              .avatar(mOwnVCardRealm != null && mOwnVCardRealm.getAvatar() != null
                  ? mOwnVCardRealm.getAvatar() : null)
              .message(message2)
              .build());
        }
      }
    }
    updateChatData();
  }

  // TODO: 24/12/2016 添加类型
  public void addMessageToAdapter(MessageRealm realm) {
    Message message1 = new Message();
    message1.paramContent = realm.getTextMessage();
    Log.d(TAG, "addMessageToAdapter: " + realm.getMainJID());
    Log.d(TAG, "addMessageToAdapter-----: " + mVCardRealm.getJid());
    if (mVCardRealm != null && realm.getMainJID().equals(mVCardRealm.getJid())) {
      mMessages.add(new ConstructMessage.Builder().itemType(MessageType.TEXT_MESSAGE_OTHER)
          .avatar(mVCardRealm != null ? mVCardRealm.getAvatar() : null)
          .message(message1)
          .build());
    } else {
      mMessages.add(new ConstructMessage.Builder().itemType(MessageType.TEXT_MESSAGE_MINE)
          .avatar(mOwnVCardRealm != null ? mOwnVCardRealm.getAvatar() : null)
          .message(message1)
          .build());
    }
    updateChatData();
  }

  @Override public void onPause() {
    super.onPause();
    //将会话的为未读数目设置为0
    if (mOwnVCardRealm != null
        && mVCardRealm != null
        && mOwnVCardRealm.getJid() != null
        && mVCardRealm.getJid() != null
        && StringSplitUtil.splitDivider(mVCardRealm.getJid()) != StringSplitUtil.splitDivider(
        mOwnVCardRealm.getJid())) {
      mRealm.executeTransaction(realm -> {
        SessionRealm sessionRealm = mRealm.where(SessionRealm.class)
            .equalTo("vcard_id", StringSplitUtil.splitDivider(mVCardRealm.getJid()))
            .findFirst();
        if (sessionRealm != null) {
          sessionRealm.setUnReadCount(0);
        }
      });
    }
  }

  @Override public void onItemClick(int position, View view) {

  }

  @Override public boolean onLongClick(View v) {
    int i = v.getId();
    if (i == R.id.chat_speak) {
    }
    return true;
  }

  @OnClick({
      R2.id.chat_add_other_information, R2.id.chat_send_message, R2.id.add_smile,
      R2.id.chat_audio_record, R2.id.chat_speak
  }) public void onClick(View v) {
    int i = v.getId();
    if (i == R.id.chat_add_other_information) {
      Log.d(TAG, "onClick: 点击了加号");
      hideKeyBoard();
      if (mChatSendOther.getVisibility() == View.VISIBLE) {
        mChatSendOther.setVisibility(View.GONE);
      } else {
        mChatSendOther.setVisibility(View.VISIBLE);
      }
    } else if (i == R.id.chat_send_message) {
      sendMyFriendMessage(mChatInput.getText().toString(), MessageExtensionType.TEXT);
    } else if (i == R.id.add_smile) {
      if (isVisibleKeyBoard()) {
        hideKeyBoard();
      } else {
        showKeyBoard();
      }
    } else if (i == R.id.chat_audio_record) {

    } else if (i == R.id.chat_speak) {
      if (mAudioRecord.getVisibility() == View.VISIBLE) {
        mChatInput.setVisibility(View.VISIBLE);
        mAudioRecord.setVisibility(View.GONE);
      } else {
        mChatInput.setVisibility(View.GONE);
        mAudioRecord.setVisibility(View.VISIBLE);
      }
    }
  }

  /**
   * 发送消息到对方，并且添加到本地
   */
  public void sendMyFriendMessage(String message, MessageExtensionType type) {
    TextMessage message1 = new TextMessage(mVCardRealm.getJid(), message);
    message1.messageType = type;
    HermesEventBus.getDefault().post(message1);
    //将消息更新到本地
    mChatInput.setText("");
  }

  public void showKeyBoard() {
    mChatMsgInputBox.showKeyboard();
  }

  public void hideKeyBoard() {
    mChatMsgInputBox.hideKeyboard();
  }

  public boolean isVisibleKeyBoard() {
    return mChatMsgInputBox.isKeyboardVisible();
  }

  @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(TAG, "onActivityResult: 进入fragment的回调");
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK) {
      if (requestCode == Constant.REQUEST_CODE_PICK_IMAGE) {
        ArrayList<ImageFile> list = data.getParcelableArrayListExtra(Constant.RESULT_PICK_IMAGE);
        uploadFile(list.get(0).getPath(), MessageExtensionType.IMAGE.toString());
      } else if (requestCode == Constant.REQUEST_CODE_PICK_FILE) {

      } else if (requestCode == Constant.REQUEST_CODE_PICK_AUDIO) {

      } else if (requestCode == Constant.REQUEST_CODE_PICK_VIDEO) {

      }
    }
  }

  @Override public void onFinishRecord(float seconds, String filePath) {
    //构建本地发送消息，开启服务器发送消息到对方
    uploadFile(filePath, MessageExtensionType.AUDIO.toString());
  }

  public void updateChatData() {
    mChatMessageAdapter.notifyDataSetChanged();
    if (mMessages.size() > 1) {
      mRecycler.scrollToPosition(mMessages.size() - 1);
    }
  }

  @Override public void onDestroyView() {
    super.onDestroyView();
    mRealm.close();
    AudioManager.getInstance().onDestroy();
  }

  public void uploadFile(String path, String type) {
    // use the FileUtils to get the actual file by uri
    File file = new File(path);
    // create RequestBody instance from file
    RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
    // MultipartBody.Part is used to send also the actual file name
    MultipartBody.Part body =
        MultipartBody.Part.createFormData("file", file.getName(), requestFile);
    RequestBody typeBody = RequestBody.create(MediaType.parse("multipart/form-data"), type);
    mUpLoadServiceApi.upload(body, typeBody)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(filePath -> {
          Log.d(TAG, "uploadFile: " + filePath);
          //发送消息添加到本地，然后发送拓展消息到对方
          if (type.equals(MessageExtensionType.IMAGE.toString())) {
            sendMyFriendMessage(filePath.filePath, MessageExtensionType.IMAGE);
          }
          if (type.equals(MessageExtensionType.AUDIO.toString())) {
            sendMyFriendMessage(filePath.filePath, MessageExtensionType.AUDIO);
          }
        }, new ErrorAction() {
          @Override public void call(Throwable throwable) {
            super.call(throwable);
            SimpleHUD.showErrorMessage(getContext(), "上传失败" + throwable.toString());
          }
        });
  }
}
