package com.skycrane.portal.web.enterprise.manage.product.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.skycrane.common.base.entity.BackstageLoginUser;
import com.skycrane.common.base.entity.EnterpriseLoginUser;
import com.skycrane.common.base.response.BaseResponse;
import com.skycrane.common.data.redis.template.RedisClientTemplate;
import com.skycrane.portal.web.enterprise.manage.product.CardExchangeReturnManage;
import com.skycrane.portal.web.enterprise.vo.input.product.CardExchangeComponentVo;
import com.skycrane.portal.web.product.dto.input.CardExchangeComponentDto;
import com.skycrane.portal.web.website.dao.CardExchangePageDao;
import com.skycrane.portal.web.website.dao.CardListDao;
import com.skycrane.portal.web.website.dto.output.QueryCardExchangeOutputDto;
import com.skycrane.portal.web.website.model.CardExchangePage;
import com.skycrane.portal.web.website.model.CardList;
import com.skycrane.portal.web.website.model.CardPageComponent;
import com.skycrane.portal.web.website.service.CardExchangePageService;
import com.skycrane.portal.web.website.service.CardPageComponentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 卡册信息服务逻辑处理
 */
@Slf4j
@Service("cardExchangeReturnManage")
public class CardExchangeReturnManageImpl implements CardExchangeReturnManage {

    @Autowired
    private RedisClientTemplate redisClientTemplate;

    @Autowired
    private CardExchangePageService cardExchangePageService;

    @Autowired
    private CardListDao cardListDao;

    @Autowired
    CardPageComponentService cardPageComponentService;

    @Autowired
    CardExchangePageDao cardExchangePageDao;

    @Override
    public BaseResponse<QueryCardExchangeOutputDto> queryCardExchangePage(String cardListCode, EnterpriseLoginUser enterpriseLoginUser) {
        QueryCardExchangeOutputDto cardListDto = cardExchangePageService.queryCardExchangePage(cardListCode);

        //查出兑换页编号
        if (cardListDto.getPageCode() == null ||  "".equals(cardListDto.getCardListCode())){
            return new BaseResponse<QueryCardExchangeOutputDto>().fail("0001","首次暂无数据提供");
        }
        String pageCode = cardListDto.getPageCode();
        //查出1001视频的基本信息
        QueryWrapper<CardPageComponent> cardVideoWrapper=new QueryWrapper<>();
        StringBuffer buff = new StringBuffer();
        buff.append("id,component_type,content,width,height,lefts,top,fontSize,color");
        cardVideoWrapper.select(buff.toString());
        cardVideoWrapper.eq(CardPageComponent.PAGE_CODE,pageCode);
        cardVideoWrapper.eq(CardPageComponent.COMPONENT_TYPE,"1001");//视频
        cardVideoWrapper.eq(CardPageComponent.IS_DELETED,false);
        List<CardPageComponent> videolist = cardPageComponentService.list(cardVideoWrapper);
        cardListDto.setVideo_list(videolist);

        QueryWrapper<CardPageComponent> cardNoteWrapper=new QueryWrapper<>();
        cardNoteWrapper.select(buff.toString());
        cardNoteWrapper.eq(CardPageComponent.PAGE_CODE,pageCode);
        cardNoteWrapper.eq(CardPageComponent.COMPONENT_TYPE,"1003");//注解
        cardNoteWrapper.eq(CardPageComponent.IS_DELETED,false);
        List<CardPageComponent> notelist = cardPageComponentService.list(cardNoteWrapper);
        cardListDto.setNote_list(notelist);

        QueryWrapper<CardPageComponent> cardTextWrapper=new QueryWrapper<>();
        cardTextWrapper.select(buff.toString());
        cardTextWrapper.eq(CardPageComponent.PAGE_CODE,pageCode);
        cardTextWrapper.eq(CardPageComponent.COMPONENT_TYPE,"1002");//文本
        cardTextWrapper.eq(CardPageComponent.IS_DELETED,false);
        List<CardPageComponent> textlist = cardPageComponentService.list(cardTextWrapper);
        cardListDto.setText_list(textlist);

        QueryWrapper<CardPageComponent> cardBgWrapper=new QueryWrapper<>();
        cardBgWrapper.select(buff.toString());
        cardBgWrapper.eq(CardPageComponent.PAGE_CODE,pageCode);
        cardBgWrapper.eq(CardPageComponent.COMPONENT_TYPE,"1006");//注解
        cardBgWrapper.eq(CardPageComponent.IS_DELETED,false);
        List<CardPageComponent> bglist = cardPageComponentService.list(cardBgWrapper);
        cardListDto.setBg_list(bglist);

        return new BaseResponse<QueryCardExchangeOutputDto>().success(cardListDto);
    }

    @Override
    public BaseResponse<String> addCardExchangePage(CardExchangeComponentVo vo, EnterpriseLoginUser enterpriseLoginUser) {
        if(vo.getCardListCode() == null || "".equals(vo.getCardListCode())){
            return new BaseResponse<String>().fail("0100","卡册编码不允许为空");
        }
        CardExchangeComponentDto dto = new CardExchangeComponentDto();
        BeanUtils.copyProperties(vo,dto);
        //先删
        int delNum = cardExchangePageService.deleteCardListCode(dto.getCardListCode(),dto.getPageCode());
        //后增
        List<CardPageComponent> list = new ArrayList<CardPageComponent>();

        QueryWrapper<CardExchangePage> cardPageWrapper = new QueryWrapper<CardExchangePage>().
                eq(CardExchangePage.CARD_LIST_CODE, dto.getCardListCode()).
                eq(CardExchangePage.IS_DELETED, false);

        CardExchangePage cardExchangePage = this.cardExchangePageDao.selectOne(cardPageWrapper);
        CardExchangePage exchangePage = new CardExchangePage();
        int flag = 0;//标志是否是第一次新增
        if (null == cardExchangePage || "".equals(cardExchangePage.getCardListCode())){
            //如果卡册兑换页面为空，则是第一次新增，需要新增卡册兑换页面表。(生成卡册兑换页面编码)
            exchangePage.setCardListCode(dto.getCardListCode());
            exchangePage.setPageCode(redisClientTemplate.getSequenceCode());
            exchangePage.setBackgroundColor(dto.getBackgroundColor());
            exchangePage.setTopicImg(dto.getTopicImg());
            exchangePage.setTopicName(dto.getTopicName());
            exchangePage.setGoodsBgColor(dto.getGoodsBgColor());
            exchangePage.setGoodsPageImg(dto.getGoodsPageImg());
            exchangePage.setGoodsPageName(dto.getGoodsPageName());
            exchangePage.setCreator(enterpriseLoginUser.getUserName());
            exchangePage.setCreateTime(new Date());
            int exchange = cardExchangePageDao.insert(exchangePage);
            log.info("卡册兑换页面插入条数"+exchange+"对象===="+exchangePage.toString());
        }else{
            if (dto.getPageCode() == null || "".equals(dto.getPageCode())){
                return new BaseResponse<String>().fail("0000","卡册页面编码不允许为空");
            }
            //不为空，则修改内容。
            UpdateWrapper<CardExchangePage> updateWrapper = new UpdateWrapper<CardExchangePage>().
                    eq(CardExchangePage.CARD_LIST_CODE, dto.getCardListCode());
            CardExchangePage cde = new CardExchangePage();
            cde.setBackgroundColor(dto.getBackgroundColor());
            cde.setTopicImg(dto.getTopicImg());
            cde.setTopicName(dto.getTopicName());
            cde.setGoodsBgColor(dto.getGoodsBgColor());
            cde.setGoodsPageImg(dto.getGoodsPageImg());
            cde.setGoodsPageName(dto.getGoodsPageName());
            this.cardExchangePageDao.update(cde,updateWrapper);
            flag = 1;
        }
        CardPageComponent cardPageComponent = new CardPageComponent();
        if (flag == 1){//表示修改
            cardPageComponent.setPageCode(dto.getPageCode());
        }else{//表示新增
            cardPageComponent.setPageCode(exchangePage.getPageCode());
        }
        if (!"".equals(dto.getCardListCode()) && delNum>=0){
            List<Map<String, Object>> video = vo.getVideo();//一个
            for (Map<String, Object> deo : video){
                cardPageComponent.setComponentType((Integer) deo.get("componentType"));
                cardPageComponent.setContent((String) deo.get("src"));
                cardPageComponent.setWidth((Integer) deo.get("width"));
                cardPageComponent.setHeight((Integer) deo.get("height"));
                cardPageComponent.setLefts((Integer) deo.get("lefts"));
                cardPageComponent.setTop((Integer) deo.get("top"));
                cardPageComponent.setFontSize((String) deo.get("fontSize"));
                cardPageComponent.setColor((String) deo.get("color"));
                cardPageComponent.setCreator(enterpriseLoginUser.getUserName());
                cardPageComponent.setCreateTime(new Date());
                list.add(cardPageComponent);
                int countVideo = cardPageComponentService.insertList(list);
                list.clear();
                log.info("=========新增视频Video=="+countVideo);
            }
            List<Map<String, Object>> note = vo.getNote();//一个
            for (Map<String, Object> node : note){
                cardPageComponent.setComponentType((Integer) node.get("componentType"));
                cardPageComponent.setContent((String) node.get("content"));
                cardPageComponent.setWidth((Integer) node.get("width"));
                cardPageComponent.setHeight((Integer) node.get("height"));
                cardPageComponent.setLefts((Integer) node.get("lefts"));
                cardPageComponent.setTop((Integer) node.get("top"));
                cardPageComponent.setFontSize((String) node.get("fontSize"));
                cardPageComponent.setColor((String) node.get("color"));
                cardPageComponent.setCreator(enterpriseLoginUser.getUserName());
                cardPageComponent.setCreateTime(new Date());
                list.add(cardPageComponent);
                int countNote = cardPageComponentService.insertList(list);
                list.clear();
                log.info("=========新增注解Note=="+countNote);
            }
            List<Map<String, Object>> testList = vo.getText_list();//五个
            for (Map<String, Object> test : testList){
                cardPageComponent.setComponentType((Integer) test.get("componentType"));
                cardPageComponent.setContent((String) test.get("content"));
                cardPageComponent.setWidth((Integer) test.get("width"));
                cardPageComponent.setHeight((Integer) test.get("height"));
                cardPageComponent.setLefts((Integer) test.get("lefts"));
                cardPageComponent.setTop((Integer) test.get("top"));
                cardPageComponent.setFontSize((String) test.get("fontSize"));
                cardPageComponent.setColor((String) test.get("color"));
                cardPageComponent.setCreator(enterpriseLoginUser.getUserName());
                cardPageComponent.setCreateTime(new Date());
                list.add(cardPageComponent);
                int countText = cardPageComponentService.insertList(list);
                list.clear();
                log.info("=========新增文本Text=="+countText);
            }
            List<Map<String, Object>> bgList = vo.getBg_list();//三个
            for (Map<String, Object> bg : bgList){
                cardPageComponent.setComponentType((Integer) bg.get("componentType"));
                cardPageComponent.setContent((String) bg.get("src"));
                cardPageComponent.setWidth((Integer) bg.get("width"));
                cardPageComponent.setHeight((Integer) bg.get("height"));
                cardPageComponent.setLefts((Integer) bg.get("lefts"));
                cardPageComponent.setTop((Integer) bg.get("top"));
                cardPageComponent.setFontSize((String) bg.get("fontSize"));
                cardPageComponent.setColor((String) bg.get("color"));
                cardPageComponent.setCreator(enterpriseLoginUser.getUserName());
                cardPageComponent.setCreateTime(new Date());
                list.add(cardPageComponent);
                int countBgbox = cardPageComponentService.insertList(list);
                list.clear();
                log.info("=========新增背景框Bgbox=="+countBgbox);
            }
        }

        return new BaseResponse<String>().success("保存成功");
    }

    /**
     * 卡册兑换页设置保存缓存,预览地址
     *
     * @param vo vo
     * @return BaseResponse<Boolean>
     */
    @Override
    public BaseResponse<String> addCardExchangePageCache(QueryCardExchangeOutputDto vo) {
        String key = "CardExchangePageCache::" + vo.getCardListCode();
        Object value = JSONObject.toJSON(vo);
        // 超时时间1小时 60*60*1000
        redisClientTemplate.set(key, value, 3600000L);
        QueryWrapper<CardList> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("exchange_url").eq("card_list_code",vo.getCardListCode());
        CardList cardList= cardListDao.selectOne(queryWrapper);
        return BaseResponse.successBack(cardList.getExchangeUrl());
    }
}
