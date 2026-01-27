/*
 * mtrandom.h
 *
 *  Created on: 2009-10-18
 *      Author: Argon.Sun
 */

#ifndef MTRANDOM_H_
#define MTRANDOM_H_

#include <random>
#include <vector>
#include <utility>

class mtrandom {
public:
	const unsigned int rand_max{ std::mt19937::max() };

	mtrandom() :
		rng() {}
	mtrandom(uint32_t seq[], size_t len) {
		std::seed_seq q(seq, seq + len);
		rng.seed(q);
	}
	explicit mtrandom(uint_fast32_t value) :
		rng(value) {}
	mtrandom(std::seed_seq& q) :
		rng(q) {}
	
	mtrandom(const mtrandom& other) = delete;
	void operator=(const mtrandom& other) = delete;

	// mersenne_twister_engine
	void seed(uint32_t seq[], size_t len) {
		std::seed_seq q(seq, seq + len);
		rng.seed(q);
	}
	void seed(uint_fast32_t value) {
		rng.seed(value);
	}
	void seed(std::seed_seq& q) {
		rng.seed(q);
	}
	uint_fast32_t rand() {
		return rng();
	}
	void discard(unsigned long long z) {
		rng.discard(z);
	}

	// old vesion, discard too many numbers
	int get_random_integer_v1(int l, int h) {
		uint32_t range = (h - l + 1);
		uint32_t secureMax = rand_max - rand_max % range;
		uint_fast32_t x;
		do {
			x = rng();
		} while (x >= secureMax);
		return l + (int)(x % range);
	}

#pragma warning(disable:4146)
	/**
	 * 生成指定范围内的随机整数（改进版本）
	 * 使用拒绝采样算法来避免模运算导致的分布偏差问题
	 *
	 * @param l 随机数范围的下界（包含）
	 * @param h 随机数范围的上界（包含）
	 * @return 返回[l, h]范围内的均匀分布随机整数
	 */
	// N % k = (N - k) % k = (-k) % k
	// discard (N % range) numbers from the left end so that it is a multiple of range
	int get_random_integer_v2(int l, int h) {
		// 计算目标范围的大小
		uint32_t range = (h - l + 1);
		// 计算需要拒绝的边界值，确保剩余范围能被range整除
		uint32_t bound = -range % range;
		// 生成随机数
		auto x = rng();
		// 拒绝采样：如果随机数小于边界值则重新生成
		while (x < bound) {
			x = rng();
		}
		// 将随机数映射到目标范围内并返回
		return l + (int)(x % range);
	}
#pragma warning(default:4146)
		// Fisher-Yates shuffle [first, last)
		/**
		 * 使用Fisher-Yates算法对向量指定范围内的元素进行随机洗牌
		 *
		 * @param v 要进行洗牌操作的向量引用
		 * @param first 洗牌范围的起始索引（包含）
		 * @param last 洗牌范围的结束索引（不包含）
		 * @param version 随机数生成器版本，1表示使用v1版本，其他值表示使用v2版本
		 */
		template<typename T>
		void shuffle_vector(std::vector<T>& v, int first, int last, int version) {
			if ((size_t)last > v.size())
				last = (int)v.size();

			// 根据版本选择对应的随机数生成器
			auto distribution = &mtrandom::get_random_integer_v2;
			if (version == 1)
				distribution = &mtrandom::get_random_integer_v1;

			// 执行Fisher-Yates洗牌算法
			for (int i = first; i < last - 1; ++i) {
				int r = (this->*distribution)(i, last - 1);
				std::swap(v[i], v[r]);
			}
		}

    /**
    * @brief 对向量进行洗牌操作
    *
    * 该函数通过调用重载版本来实现对整个向量的洗牌操作，默认从索引0开始，
    * 到向量末尾结束，并使用默认的洗牌参数。
    *
    * @param v 待洗牌的向量引用，类型为T的元素组成的std::vector
    *
    * @return 无返回值
    */
	template<typename T>
	void shuffle_vector(std::vector<T>& v) {
		shuffle_vector(v, 0, (int)v.size(), 2);
	}

	template<typename T>
	void shuffle_vector(std::vector<T>& v, int first, int last) {
		shuffle_vector(v, first, last, 2);
	}

private:
	std::mt19937 rng;
};


#endif /* MTRANDOM_H_ */
