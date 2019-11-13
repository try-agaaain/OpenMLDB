/*
 * tablet_server_impl.h
 * Copyright (C) 4paradigm.com 2019 wangtaize <wangtaize@4paradigm.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef SRC_TABLET_TABLET_SERVER_IMPL_H_
#define SRC_TABLET_TABLET_SERVER_IMPL_H_

#include "proto/tablet.pb.h"
#include "base/spin_lock.h"
#include "vm/table_mgr.h"
#include "brpc/server.h"

namespace fesql {
namespace tablet {

using ::google::protobuf::RpcController;
using ::google::protobuf::Closure;

// TODO opt db tid pid structure

typedef std::map<uint32_t, std::shared_ptr<vm::TableStatus>> Partition;

typedef std::map<uint32_t, 
        std::map<uint32_t, std::shared_ptr<vm::TableStatus>>> Table;

typedef std::map<std::string, 
                std::map<uint32_t, 
                std::map<uint32_t, std::shared_ptr<vm::TableStatus>>>> Tables;

typedef std::map<std::string,
                 std::map<std::string, uint32_t>> TableNames;

class TabletServerImpl : public TabletServer {

 public:
    TabletServerImpl();
    ~TabletServerImpl();

    void CreateTable(RpcController* ctrl,
            const CreateTableRequest* request,
            CreateTableResponse* response,
            Closure* done);

    void Query(RpcController* ctrl,
            const QueryRequest* request,
            QueryResponse* response,
            Closure* done);

 private:
    inline std::shared_ptr<vm::TableStatus> GetTableLocked(const std::string& db,
            uint32_t tid, uint32_t pid);

    std::shared_ptr<vm::TableStatus> GetTableUnLocked(const std::string& db,
            uint32_t tid, uint32_t pid);

    bool AddTableUnLocked(std::shared_ptr<vm::TableStatus>& table);
    inline bool AddTableLocked(std::shared_ptr<vm::TableStatus>& table);

 private:
    base::SpinMutex slock_;
    Tables tables_;
    TableNames table_names_;
};

}  // namespace tablet
}  // namespace fesql
#endif  // SRC_TABLET_TABLET_SERVER_IMPL_H_
