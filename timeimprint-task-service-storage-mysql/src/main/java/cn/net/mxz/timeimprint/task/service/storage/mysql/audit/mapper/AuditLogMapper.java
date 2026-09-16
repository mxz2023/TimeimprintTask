package cn.net.mxz.timeimprint.task.service.storage.mysql.audit.mapper;

import cn.net.mxz.timeimprint.task.service.storage.mysql.audit.row.AuditLogRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper {

    void insert(AuditLogRow row);
}
