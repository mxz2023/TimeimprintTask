package cn.net.mxz.timeimprint.task.service.storage.mysql.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.row.AuditLogRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper {

    void insert(AuditLogRow row);
}
