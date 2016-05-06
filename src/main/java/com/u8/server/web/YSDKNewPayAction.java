package com.u8.server.web;

import com.u8.server.common.UActionSupport;
import com.u8.server.constants.PayState;
import com.u8.server.data.UChannel;
import com.u8.server.data.UGame;
import com.u8.server.data.UOrder;
import com.u8.server.data.UUser;
import com.u8.server.log.Log;
import com.u8.server.sdk.ysdk.PayRequest;
import com.u8.server.sdk.ysdk.YSDKApi;
import com.u8.server.service.UChannelManager;
import com.u8.server.service.UOrderManager;
import com.u8.server.service.UUserManager;
import com.u8.server.utils.EncryptUtils;
import com.u8.server.utils.TimeFormater;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Namespace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.Date;

/**
 * YSDK新的支付流程
 * 客户端支付之前，先访问query接口查询余额，余额充足，则直接用余额支付
 * 支付之后，调用扣款接口
 * 处理成功之后，和其他渠道一样，采用订单的方式
 * Created by xiaohei on 16/5/6.
 */

@Controller
@Namespace("/pay/ysdknew")
public class YSDKNewPayAction extends UActionSupport {

    private Long orderID;     //下单时候的订单号
    private int channelID;      //当前渠道ID
    private int userID;         //当前用户ID
    private int accountType;    //0:QQ;1:微信
    private String openID;      //从手Q登录态或微信登录态中获取的openid的值
    private String openKey;     //从手Q登录态或微信登录态中获取的access_token 的值
    private String pf;          //平台来源
    private String pfkey;       //跟平台来源和openkey根据规则生成的一个密钥串
    private String zoneid;      //账户分区ID
    private String sign;        //签名验证

    @Autowired
    private UChannelManager channelManager;

    @Autowired
    private UUserManager userManager;

    @Autowired
    private UOrderManager orderManager;


    /***
     * 客户端正式充值之前先调用该api查询余额
     */
    @Action("query")
    public void queryMoney(){

        try{

            Log.e("orderID=" + orderID);
            Log.e("channelID=" + channelID);
            Log.e("userID=" + userID);
            Log.e("accountType=" + accountType);
            Log.e("openID=" + openID);
            Log.e("openKey=" + openKey);
            Log.e("pf=" +  pf);
            Log.e("pfkey=" + pfkey);
            Log.e("zoneid=" + zoneid);

            UChannel channel = channelManager.queryChannel(this.channelID);
            if(channel == null){
                Log.d("The channel is not exists. channelID:%s", this.channelID);
                this.renderState(false);
                return;
            }

            UUser user = userManager.getUser(this.userID);
            if(user == null){
                Log.e("the user is not exists. userID:%s ", this.userID);
                this.renderState(false);
                return;
            }

            if(!user.getChannelUserID().equals(this.openID)){
                Log.e("the userID %s is not matched the channel userID %s. ", this.userID, this.openID);
                this.renderState(false);
                return;
            }

            if(!isSignOK(channel.getGame())){
                Log.e("the sign is not valid. sign:"+this.sign);
                this.renderState(false);
                return;
            }

            if(!channel.isPayOpen()){
                Log.e("the channel %s of game %s had closed pay. you need to open the pay first.", this.channelID, channel.getGame().getName());
                this.renderState(false);
                return;
            }

            PayRequest req = new PayRequest();
            req.setUser(user);
            req.setAccountType(accountType);
            req.setOpenID(openID);
            req.setOpenKey(openKey);
            req.setPf(pf);
            req.setPfkey(pfkey);
            req.setZoneid(zoneid);
            req.setOrderID(orderID);

            JSONObject result = YSDKApi.queryMoney(channel, req);
            if(result == null){
                this.renderState(false);
            }

            Log.d("the query money result:");
            Log.d(result.toString());

            int money = result.getInt("balance");

            this.renderQueryResult(money);

        }catch(Exception e){
            try {
                renderState(false);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    @Action("charge")
    public void charge(){

        try{

            Log.e("orderID=" + orderID);
            Log.e("channelID=" + channelID);
            Log.e("userID=" + userID);
            Log.e("accountType=" + accountType);
            Log.e("openID=" + openID);
            Log.e("openKey=" + openKey);
            Log.e("pf=" +  pf);
            Log.e("pfkey=" + pfkey);
            Log.e("zoneid=" + zoneid);

            UChannel channel = channelManager.queryChannel(this.channelID);
            if(channel == null){
                Log.d("The channel is not exists. channelID:%s", this.channelID);
                this.renderState(false);
                return;
            }

            UUser user = userManager.getUser(this.userID);
            if(user == null){
                Log.e("the user is not exists. userID:%s ", this.userID);
                this.renderState(false);
                return;
            }

            UOrder order = orderManager.getOrder(this.orderID);
            if(order == null){
                Log.e("the order is not exists. orderID:%s", this.orderID);
                this.renderState(false);
                return;
            }

            if(!user.getChannelUserID().equals(this.openID)){
                Log.e("the userID %s is not matched the channel userID %s. ", this.userID, this.openID);
                this.renderState(false);
                return;
            }

            if(!isSignOK(channel.getGame())){
                Log.e("the sign is not valid. sign:"+this.sign);
                this.renderState(false);
                return;
            }

            if(!channel.isPayOpen()){
                Log.e("the channel %s of game %s had closed pay. you need to open the pay first.", this.channelID, channel.getGame().getName());
                this.renderState(false);
                return;
            }

            if(StringUtils.isEmpty(channel.getCpConfig())){
                Log.e("you should put the pay ratio of ysdk in the cpConfig field of UChannel");
                this.renderState(false);
                return;
            }

            PayRequest req = new PayRequest();
            req.setUser(user);
            req.setAccountType(accountType);
            req.setOpenID(openID);
            req.setOpenKey(openKey);
            req.setPf(pf);
            req.setPfkey(pfkey);
            req.setZoneid(zoneid);
            req.setOrderID(orderID);

            //coin 是游戏币， 下单的时候order中money是分， 转为元之后， 再乘以应用宝后台的游戏币兑换比例，得到游戏币的数量
            int coin = (int)(order.getMoney() / 100f * Integer.valueOf(channel.getCpConfig()));


            JSONObject result = YSDKApi.charge(coin, channel, req);
            if(result == null){
                Log.e("charge to ysdk failed.result is null");
                this.renderState(false);
                return;
            }

            Log.d("the charge result is :");
            Log.d(result.toString());

            order.setChannelOrderID("");
            order.setRealMoney(order.getMoney());
            order.setSdkOrderTime(TimeFormater.format_yyyyMMddHHmmss(new Date()));
            order.setCompleteTime(new Date());
            order.setState(PayState.STATE_SUC);
            orderManager.saveOrder(order);
            //SendAgent.sendCallbackToServer(this.orderManager, order);

            this.renderState(true);


        }catch(Exception e){
            try {
                renderState(false);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        }


    }

    private boolean isSignOK(UGame game){

        StringBuilder sb = new StringBuilder();
        sb.append("accountType=").append(accountType)
                .append("channelID=").append(channelID)
                .append("openID=").append(openID)
                .append("openKey=").append(openKey)
                .append("orderID=").append(orderID)
                .append("pf=").append(pf)
                .append("pfkey=").append(pfkey)
                .append("userID=").append(userID)
                .append("zoneid=").append(zoneid)
                .append(game.getAppkey());

        String signLocal = EncryptUtils.md5(sb.toString()).toLowerCase();

        return signLocal.equals(this.sign);

    }

    private void renderState(boolean suc) throws IOException {

        JSONObject json = new JSONObject();

        if(suc){
            json.put("state", 1);
        }else{
            json.put("state", 0);
        }

        super.renderJson(json.toString());
    }

    private void renderQueryResult(int money) throws IOException {

        JSONObject json = new JSONObject();
        json.put("state", 1);
        json.put("money", money);

        super.renderJson(json.toString());
    }

    public Long getOrderID() {
        return orderID;
    }

    public void setOrderID(Long orderID) {
        this.orderID = orderID;
    }

    public int getChannelID() {
        return channelID;
    }

    public void setChannelID(int channelID) {
        this.channelID = channelID;
    }

    public int getUserID() {
        return userID;
    }

    public void setUserID(int userID) {
        this.userID = userID;
    }

    public int getAccountType() {
        return accountType;
    }

    public void setAccountType(int accountType) {
        this.accountType = accountType;
    }

    public String getOpenID() {
        return openID;
    }

    public void setOpenID(String openID) {
        this.openID = openID;
    }

    public String getOpenKey() {
        return openKey;
    }

    public void setOpenKey(String openKey) {
        this.openKey = openKey;
    }

    public String getPf() {
        return pf;
    }

    public void setPf(String pf) {
        this.pf = pf;
    }

    public String getPfkey() {
        return pfkey;
    }

    public void setPfkey(String pfkey) {
        this.pfkey = pfkey;
    }

    public String getZoneid() {
        return zoneid;
    }

    public void setZoneid(String zoneid) {
        this.zoneid = zoneid;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }
}
