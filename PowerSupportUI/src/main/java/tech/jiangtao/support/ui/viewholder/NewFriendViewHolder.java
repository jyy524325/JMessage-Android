package tech.jiangtao.support.ui.viewholder;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import butterknife.BindView;
import butterknife.ButterKnife;
import tech.jiangtao.support.kit.eventbus.RecieveFriend;
import tech.jiangtao.support.ui.R;
import tech.jiangtao.support.ui.R2;
import tech.jiangtao.support.ui.adapter.EasyViewHolder;
import tech.jiangtao.support.ui.model.group.InvitedInfo;
import xiaofei.library.hermeseventbus.HermesEventBus;

/**
 * Class: NewFriendViewHolder </br>
 * Description: 好友页面 </br>
 * Creator: kevin </br>
 * Email: jiangtao103cp@gmail.com </br>
 * Date: 08/01/2017 6:13 PM</br>
 * Update: 08/01/2017 6:13 PM </br>
 **/

public class NewFriendViewHolder extends EasyViewHolder<InvitedInfo> {
  @BindView(R2.id.new_friend_avatar) ImageView mNewFriendAvatar;
  @BindView(R2.id.tv_messageInfo) TextView mNewFriendNickname;
  @BindView(R2.id.new_friend_agree) TextView mNewFriendAgree;
  @BindView(R2.id.new_friend_refused) TextView mNewFriendRefused;
  private Context mContext;

  public NewFriendViewHolder(Context context, ViewGroup parent) {
    super(context, parent, R.layout.list_item_new_friend);
    ButterKnife.bind(this,itemView);
    mContext = context;
  }


  @Override
  public void bindTo(int position, InvitedInfo info) {
    Glide.with(mContext)
            .load(info.avatar != null ? Uri.parse(info.avatar) : null)
            .centerCrop()
            .error(R.mipmap.ic_chat_default)
            .placeholder(R.mipmap.ic_chat_default)
            .into(mNewFriendAvatar);
    mNewFriendNickname.setText(info.inviteType.equals("FRIEND")?info.nickName+"请求添加您为好友。":info.nickName+"邀请你进入**群");
      //TODO 缺少邀请要加入的群名
    mNewFriendAgree.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        HermesEventBus.getDefault().post(new RecieveFriend(true));
        mNewFriendAgree.setText("成功");
        mNewFriendAgree.setEnabled(false);
      }
    });
    mNewFriendRefused.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        HermesEventBus.getDefault().post(new RecieveFriend(false));
        mNewFriendRefused.setText("拒绝成功");
        mNewFriendRefused.setEnabled(false);
      }
    });
  }
}
