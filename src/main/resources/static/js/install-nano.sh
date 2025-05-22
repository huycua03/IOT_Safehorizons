#!/bin/bash

# Script cài đặt nano cho container Docker
echo "Đang cập nhật danh sách gói..."
apt-get update

echo "Đang cài đặt nano..."
apt-get install -y nano

echo "Kiểm tra cài đặt..."
which nano

echo "Cài đặt nano hoàn tất." 