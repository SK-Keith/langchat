package cn.tycoding.langchat.aigc.mapper;

import cn.tycoding.langchat.aigc.entity.AigcMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author tycoding
 * @since 2024/1/4
 */
@Mapper
public interface AigcMessageMapper extends BaseMapper<AigcMessage> {

}

