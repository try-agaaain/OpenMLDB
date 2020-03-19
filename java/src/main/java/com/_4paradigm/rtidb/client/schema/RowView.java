package com._4paradigm.rtidb.client.schema;

import com._4paradigm.rtidb.client.TabletException;
import com._4paradigm.rtidb.client.type.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class RowView {

    private final static Logger logger = LoggerFactory.getLogger(RowView.class);
    private ByteBuffer row = null;
    private int size = 0;
    List<ColumnDesc> schema = new ArrayList<>();
    private int string_field_cnt = 0;
    private int str_field_start_offset = 0;
    private int str_addr_length = 0;
    private List<Integer> offset_vec = new ArrayList<>();
    private boolean is_valid = false;


    public RowView(List<ColumnDesc> schema) {
        this.schema = schema;
        this.is_valid = true;
        if (schema.size() == 0) {
            is_valid = false;
            return;
        }
        init();
    }

    public RowView(List<ColumnDesc> schema, ByteBuffer row, int size) {
        this.schema = schema;
        this.is_valid = true;
        this.size = size;
        if (row.order() == ByteOrder.BIG_ENDIAN) {
            row = row.order(ByteOrder.LITTLE_ENDIAN);
        }
        this.row = row;
        if (schema.size() == 0) {
            is_valid = false;
            return;
        }
        if (init()) {
            reset(row, size);
        }
    }

    private boolean init() {
        str_field_start_offset = RowCodecCommon.HEADER_LENGTH + RowCodecCommon.getBitMapSize(schema.size());
        for (int idx = 0; idx < schema.size(); idx++) {
            ColumnDesc column = schema.get(idx);
            if (column.getDataType() == DataType.Varchar) {
                offset_vec.add(string_field_cnt);
                string_field_cnt++;
            } else {
                if (RowCodecCommon.TYPE_SIZE_MAP.get(column.getDataType()) == null) {
                    is_valid = false;
                    logger.warn("type is not supported");
                    return false;
                } else {
                    offset_vec.add(str_field_start_offset);
                    str_field_start_offset += RowCodecCommon.TYPE_SIZE_MAP.get(column.getDataType());
                }
            }
        }
        return true;
    }

    public boolean reset(ByteBuffer row, int size) {
        if (schema.size() == 0 || row == null || size <= RowCodecCommon.HEADER_LENGTH ||
                row.getInt(row.position() + RowCodecCommon.VERSION_LENGTH) != size) {
            is_valid = false;
            return false;
        }
        if (row.order() == ByteOrder.BIG_ENDIAN) {
            row = row.order(ByteOrder.LITTLE_ENDIAN);
        }
        this.row = row;
        this.size = size;
        str_addr_length = RowCodecCommon.getAddrLength(size);
        return true;
    }

    private boolean reset(ByteBuffer row) {
        if (schema.size() == 0 || row == null) {
            is_valid = false;
            return false;
        }
        if (row.order() == ByteOrder.BIG_ENDIAN) {
            row = row.order(ByteOrder.LITTLE_ENDIAN);
        }
        this.row = row;
        this.size = row.getInt(RowCodecCommon.VERSION_LENGTH);
        if (this.size < RowCodecCommon.HEADER_LENGTH) {
            is_valid = false;
            return false;
        }
        str_addr_length = RowCodecCommon.getAddrLength(size);
        return true;
    }

    private boolean checkValid(int idx, DataType type) {
        if (row == null || !is_valid) {
            return false;
        }
        if (idx >= schema.size()) {
            return false;
        }
        ColumnDesc column = schema.get(idx);
        if (column.getDataType() != type) {
            return false;
        }
        return true;
    }

    public boolean isNull(int idx) {
        return isNull(row, idx);
    }

    public boolean isNull(ByteBuffer row, int idx) {
        if (row.order() == ByteOrder.BIG_ENDIAN) {
            row = row.order(ByteOrder.LITTLE_ENDIAN);
        }
        int ptr = RowCodecCommon.HEADER_LENGTH + (idx >> 3);
        byte bt = row.get(row.position() + ptr);
        int ret = bt & (1 << (idx & 0x07));
        return (ret > 0) ? true : false;
    }

    public static int getSize(ByteBuffer row) {
        return row.getInt(row.position() + RowCodecCommon.VERSION_LENGTH);
    }

    public Boolean getBool(int idx) throws TabletException {
        return (Boolean) getValue(row, idx, DataType.Bool);
    }

    public Integer getInt32(int idx) throws TabletException {
        return (Integer) getValue(row, idx, DataType.Int);
    }

    public Long getTimestamp(int idx) throws TabletException {
        return (Long) getValue(row, idx, DataType.Timestamp);
    }

    public Long getInt64(int idx) throws TabletException {
        return (Long) getValue(row, idx, DataType.BigInt);
    }

    public Short getInt16(int idx) throws TabletException {
        return (Short) getValue(row, idx, DataType.SmallInt);
    }

    public Float getFloat(int idx) throws TabletException {
        return (Float) getValue(row, idx, DataType.Float);
    }

    public Double getDouble(int idx) throws TabletException {
        return (Double) getValue(row, idx, DataType.Double);
    }

    public Object getIntegersNum(ByteBuffer row, int idx, DataType type) throws TabletException {
        switch (type) {
            case SmallInt: {
                return (Short) getValue(row, idx, type);
            }
            case Int: {
                return (Integer) getValue(row, idx, type);
            }
            case Timestamp:
            case BigInt: {
                return (Long) getValue(row, idx, type);
            }
            default:
                throw new TabletException("unsupported data type");
        }
    }

    public Object getValue(int idx, DataType type) throws TabletException {
        return getValue(this.row, idx, type);
    }

    public Object getValue(ByteBuffer row, int idx, DataType type) throws TabletException {
        if (schema.size() == 0 || row == null || idx >= schema.size()) {
            throw new TabletException("input mistake");
        }
        if (row.order() == ByteOrder.BIG_ENDIAN) {
            row = row.order(ByteOrder.LITTLE_ENDIAN);
        }
        ColumnDesc column = schema.get(idx);
        if (column.getDataType() != type) {
            throw new TabletException("data type mismatch");
        }
        int size = getSize(row);
        if (size <= RowCodecCommon.HEADER_LENGTH) {
            throw new TabletException("row size is not bigger than header length");
        }
        if (isNull(row, idx)) {
            return null;
        }
        Object val = null;
        int offset = offset_vec.get(idx) + row.position();
        switch (type) {
            case Bool: {
                int v = row.get(offset);
                if (v == 1) {
                    val = true;
                } else {
                    val = false;
                }
                break;
            }
            case SmallInt:
                val = row.getShort(offset);
                break;
            case Int:
                val = row.getInt(offset);
                break;
            case Timestamp:
            case BigInt:
                val = row.getLong(offset);
                break;
            case Float:
                val = row.getFloat(offset);
                break;
            case Double:
                val = row.getDouble(offset);
                break;
            case Varchar:
                int field_offset = offset;
                int next_str_field_offset = 0;
                if (field_offset < string_field_cnt - 1) {
                    next_str_field_offset = field_offset + 1;
                }
                return getStrField(row, field_offset - row.position(), next_str_field_offset,
                        str_field_start_offset, RowCodecCommon.getAddrLength(size));
            default:
                throw new TabletException("unsupported data type");
        }
        return val;
    }

    public String getString(int idx) throws TabletException {
        return (String) getValue(row, idx, DataType.Varchar);
    }

    public String getStrField(ByteBuffer row, int field_offset,
                              int next_str_field_offset, int str_start_offset,
                              int addr_space) throws TabletException {
        if (row == null) {
            throw new TabletException("row is null");
        }
        if (row.order() == ByteOrder.BIG_ENDIAN) {
            row = row.order(ByteOrder.LITTLE_ENDIAN);
        }
        int row_with_offset = str_start_offset;
        int str_offset = 0;
        int next_str_offset = 0;
        switch (addr_space) {
            case 1: {
                str_offset = row.get(row_with_offset + field_offset * addr_space) & 0xFF;
                if (next_str_field_offset > 0) {
                    next_str_offset = row.get(row_with_offset + next_str_field_offset * addr_space) & 0xFF;
                }
                break;
            }
            case 2: {
                str_offset = row.getShort(row_with_offset + field_offset * addr_space) & 0xFFFF;
                if (next_str_field_offset > 0) {
                    next_str_offset = row.getShort(row_with_offset + next_str_field_offset * addr_space) & 0xFFFF;
                }
                break;
            }
            case 3: {
                int cur_row_with_offset = row_with_offset + field_offset * addr_space;
                str_offset = row.get(cur_row_with_offset) & 0xFF;
                str_offset = (str_offset << 8) + (row.get((cur_row_with_offset + 1)) & 0xFF);
                str_offset = (str_offset << 8) + (row.get((cur_row_with_offset + 2)) & 0xFF);
                if (next_str_field_offset > 0) {
                    int next_row_with_offset = row_with_offset + next_str_field_offset * addr_space;
                    next_str_offset = row.get((next_row_with_offset)) & 0xFF;
                    next_str_offset = (next_str_offset << 8) + (row.get(next_row_with_offset + 1) & 0xFF);
                    next_str_offset = (next_str_offset << 8) + (row.get(next_row_with_offset + 2) & 0xFF);
                }
                break;
            }
            case 4: {
                str_offset = row.getInt(row_with_offset + field_offset * addr_space);
                if (next_str_field_offset > 0) {
                    next_str_offset = row.getInt(row_with_offset + next_str_field_offset * addr_space);
                }
                break;
            }
            default: {
                throw new TabletException("addr_space mistakes");
            }
        }
        int len;
        if (next_str_field_offset <= 0) {
            int total_length = row.getInt(row.position() + RowCodecCommon.VERSION_LENGTH);
            len = total_length - str_offset;
        } else {
            len = next_str_offset - str_offset;
        }
        byte[] arr = new byte[len];
        row.position(str_offset);
        row.get(arr);
        return new String(arr, RowCodecCommon.CHARSET);
    }
}
